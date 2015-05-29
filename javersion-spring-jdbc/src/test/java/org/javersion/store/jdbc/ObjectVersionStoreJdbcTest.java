package org.javersion.store.jdbc;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.javersion.path.PropertyPath.ROOT;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.annotation.Resource;

import org.javersion.core.Revision;
import org.javersion.core.Version;
import org.javersion.core.VersionGraph;
import org.javersion.object.ObjectVersion;
import org.javersion.object.ObjectVersionBuilder;
import org.javersion.object.ObjectVersionGraph;
import org.javersion.object.ObjectVersionManager;
import org.javersion.object.Versionable;
import org.javersion.path.PropertyPath;
import org.javersion.store.PersistenceTestConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.support.TransactionTemplate;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mysema.query.sql.SQLQueryFactory;
import com.mysema.query.types.path.StringPath;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = PersistenceTestConfiguration.class)
public class ObjectVersionStoreJdbcTest {

    @Versionable
    public static class Price {
        public final BigDecimal amount;
        private Price() {
            this(null);
        }
        public Price(BigDecimal amount) {
            this.amount = amount;
        }
    }

    @Versionable
    public static class Product {
        public long id;
        public String name;
        public Price price;
        public List<String> tags;
        public double vat;
        public boolean outOfStock;
    }

    private final ObjectVersionManager<Product, Void> versionManager = new ObjectVersionManager<Product, Void>(Product.class).init();

    private final JVersion jVersion = new JVersion("PUBLIC", "", "version");
    private final StringPath jVersionId = new StringPath(jVersion, "DOC_ID");

    @Resource
    ObjectVersionStoreJdbc<String, Void> versionStore;

    @Resource
    TransactionTemplate transactionTemplate;

    @Resource
    SQLQueryFactory queryFactory;

    @Test
    public void insert_and_load() {
        String docId = randomUUID().toString();

        assertThat(versionStore.load(docId).isEmpty()).isTrue();

        Product product = new Product();
        product.id = 123l;
        product.name = "product";

        ObjectVersion<Void> versionOne = versionManager.versionBuilder(product).build();
        versionStore.append(docId, versionManager.getVersionNode(versionOne.revision));
        assertThat(versionStore.load(docId).isEmpty()).isTrue();

        versionStore.publish();
        VersionGraph versionGraph = versionStore.load(docId);
        assertThat(versionGraph.isEmpty()).isFalse();
        assertThat(versionGraph.getTip().getVersion()).isEqualTo(versionOne);

        product.price = new Price(new BigDecimal(10));
        product.tags = ImmutableList.of("tag", "and", "another");
        product.vat = 22.5;

        versionStore.append(docId, versionManager.versionBuilder(product).buildVersionNode());

        product.outOfStock = true;

        ObjectVersion<Void> lastVersion = versionManager.versionBuilder(product).build();
        versionStore.append(docId, versionManager.getVersionNode(lastVersion.revision));
        assertThat(versionStore.load(docId).getTip().getVersion()).isEqualTo(versionOne);

        versionStore.publish();
        versionGraph = versionStore.load(docId);
        assertThat(versionGraph.getTip().getVersion()).isEqualTo(lastVersion);

        versionManager.init(versionGraph);
        Product persisted = versionManager.mergeBranches(Version.DEFAULT_BRANCH).object;
        assertThat(persisted.id).isEqualTo(product.id);
        assertThat(persisted.name).isEqualTo(product.name);
        assertThat(persisted.outOfStock).isEqualTo(product.outOfStock);
        assertThat(persisted.price.amount).isEqualTo(product.price.amount);
        assertThat(persisted.tags).isEqualTo(product.tags);
        assertThat(persisted.vat).isEqualTo(product.vat);
    }

    @Test
    public void load_version_with_empty_changeset() {
        String docId = randomUUID().toString();
        ObjectVersion<Void> emptyVersion = new ObjectVersionBuilder<Void>().build();
        ObjectVersionGraph<Void> versionGraph = ObjectVersionGraph.init(emptyVersion);
        versionStore.append(docId, versionGraph.getTip());
        versionStore.publish();
        versionGraph = versionStore.load(docId);
        List<Version<PropertyPath, Object, Void>> versions = versionGraph.getVersions();
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0)).isEqualTo(emptyVersion);
    }

    @Test
    public void ordinal_is_assigned_by_publish() throws InterruptedException {
        final CountDownLatch firstInsertDone = new CountDownLatch(1);
        final CountDownLatch secondInsertDone = new CountDownLatch(1);

        final String docId = randomUUID().toString();
        final Revision r1 = new Revision();
        final Revision r2 = new Revision();

        new Thread(() -> {
            transactionTemplate.execute(status -> {
                ObjectVersion<Void> version1 = ObjectVersion.<Void>builder(r1)
                        .changeset(ImmutableMap.of(ROOT.property("concurrency"), " slow"))
                        .build();
                versionStore.append(docId, ObjectVersionGraph.init(version1).getTip());

                // First insert is done, but transaction is not committed yet
                firstInsertDone.countDown();
                try {
                    // Wait until second insert is committed before committing this
                    secondInsertDone.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
        }).start();

        // Wait until first insert is done, but not committed yet
        firstInsertDone.await();

        ObjectVersion<Void> version2 = ObjectVersion.<Void>builder(r2)
                .changeset(ImmutableMap.of(ROOT.property("concurrency"), "fast"))
                .build();
        versionStore.append(docId, ObjectVersionGraph.init(version2).getTip());
        versionStore.publish();

        long count = queryFactory.from(jVersion)
                .where(jVersionId.eq(docId))
                .count();
        assertThat(count).isEqualTo(1);

        // Let the first transaction commit
        secondInsertDone.countDown();

        count = queryFactory.from(jVersion)
                .where(jVersionId.eq(docId))
                .count();
        assertThat(count).isEqualTo(2);

        // Before versionStore.publish(), unpublished version should not have ordinal
        Map<Revision, Long> ordinals = queryFactory.from(jVersion)
                .where(jVersionId.eq(docId))
                .map(jVersion.revision, jVersion.ordinal);
        assertThat(ordinals.get(r1)).isNull();
        assertThat(ordinals.get(r2)).isNotNull();

        // versionStore.publish() should assign ordinal
        versionStore.publish();
        ordinals = queryFactory.from(jVersion)
                .where(jVersionId.eq(docId))
                .map(jVersion.revision, jVersion.ordinal);
        assertThat(ordinals.get(r1)).isEqualTo(ordinals.get(r2) + 1);
    }
}
