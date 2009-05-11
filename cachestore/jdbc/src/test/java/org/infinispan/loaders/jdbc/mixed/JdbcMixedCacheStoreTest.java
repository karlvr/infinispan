package org.infinispan.loaders.jdbc.mixed;

import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.container.entries.InternalEntryFactory;
import org.infinispan.loaders.CacheLoaderException;
import org.infinispan.loaders.CacheStore;
import org.infinispan.loaders.jdbc.TableManipulation;
import org.infinispan.loaders.jdbc.connectionfactory.ConnectionFactory;
import org.infinispan.loaders.jdbc.connectionfactory.ConnectionFactoryConfig;
import org.infinispan.loaders.jdbc.stringbased.DefaultKey2StringMapper;
import org.infinispan.loaders.jdbc.stringbased.Person;
import org.infinispan.marshall.TestObjectStreamMarshaller;
import org.infinispan.test.fwk.UnitTestDatabaseManager;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Set;

/**
 * Tester class for {@link JdbcMixedCacheStore}
 *
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = "functional", testName = "loaders.jdbc.mixed.JdbcMixedCacheStoreTest")
public class JdbcMixedCacheStoreTest {

   private CacheStore cacheStore;
   private TableManipulation stringsTm;
   private TableManipulation binaryTm;
   private ConnectionFactoryConfig cfc;

   private static final Person MIRCEA = new Person("Mircea", "Markus", 28);
   private static final Person MANIK = new Person("Manik", "Surtani", 18);


   @BeforeTest
   public void createCacheStore() throws CacheLoaderException {
      stringsTm = UnitTestDatabaseManager.buildDefaultTableManipulation();
      stringsTm.setTableName("STRINGS_TABLE");
      binaryTm = UnitTestDatabaseManager.buildDefaultTableManipulation();
      binaryTm.setTableName("BINARY_TABLE");
      cfc = UnitTestDatabaseManager.getUniqueConnectionFactoryConfig();
      JdbcMixedCacheStoreConfig cacheStoreConfig = new JdbcMixedCacheStoreConfig(cfc, binaryTm, stringsTm);
      cacheStoreConfig.setPurgeSynchronously(true);

      cacheStoreConfig.setKey2StringMapperClass(DefaultKey2StringMapper.class.getName());
      cacheStore = new JdbcMixedCacheStore();
      cacheStore.init(cacheStoreConfig, null, new TestObjectStreamMarshaller(true));
      cacheStore.start();
   }

   @AfterMethod
   public void clearStore() throws Exception {
      cacheStore.clear();
      assertBinaryRowCount(0);
      assertStringsRowCount(0);
   }

   @AfterTest
   public void destroyStore() throws CacheLoaderException {
      cacheStore.stop();
      UnitTestDatabaseManager.shutdownInMemoryDatabase(cfc);
   }

   public void testMixedStore() throws Exception {
      cacheStore.store(InternalEntryFactory.create("String", "someValue"));
      assertStringsRowCount(1);
      assertBinaryRowCount(0);
      cacheStore.store(InternalEntryFactory.create(MIRCEA, "value"));
      assertStringsRowCount(1);
      assertStringsRowCount(1);
      assert cacheStore.load(MIRCEA).getValue().equals("value");
      assert cacheStore.load("String").getValue().equals("someValue");
   }

   public void testMultipleEntriesWithSameHashCode() throws Exception {
      Person one = new Person("Mircea", "Markus", 28);
      Person two = new Person("Manik", "Surtani", 28);
      one.setHashCode(100);
      two.setHashCode(100);
      cacheStore.store(InternalEntryFactory.create(one, "value"));
      assertBinaryRowCount(1);
      assertStringsRowCount(0);
      cacheStore.store(InternalEntryFactory.create(two, "otherValue"));
      assertBinaryRowCount(1); //both go to same bucket
      assertStringsRowCount(0);
      assert cacheStore.load(one).getValue().equals("value");
      assert cacheStore.load(two).getValue().equals("otherValue");
   }

   public void testClear() throws Exception {
      cacheStore.store(InternalEntryFactory.create("String", "someValue"));
      assertRowCounts(0, 1);
      cacheStore.store(InternalEntryFactory.create(MIRCEA, "value"));
      assertRowCounts(1, 1);
      cacheStore.clear();
      assertRowCounts(0, 0);
   }

   public void testMixedFromAndToStream() throws Exception {
      cacheStore.store(InternalEntryFactory.create("String", "someValue"));
      cacheStore.store(InternalEntryFactory.create("String2", "someValue"));
      cacheStore.store(InternalEntryFactory.create(MIRCEA, "value1"));
      cacheStore.store(InternalEntryFactory.create(MANIK, "value2"));
      assertRowCounts(2, 2);
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
      cacheStore.toStream(objectOutputStream);
      cacheStore.clear();
      assertRowCounts(0, 0);
      cacheStore.fromStream(new ObjectInputStream(new ByteArrayInputStream(byteArrayOutputStream.toByteArray())));
      assertRowCounts(2, 2);
      assert cacheStore.load("String").getValue().equals("someValue");
      assert cacheStore.load("String2").getValue().equals("someValue");
      assert cacheStore.load(MIRCEA).getValue().equals("value1");
      assert cacheStore.load(MANIK).getValue().equals("value2");
   }

   public void testLoadAll() throws Exception {
      InternalCacheEntry first = InternalEntryFactory.create("String", "someValue");
      InternalCacheEntry second = InternalEntryFactory.create("String2", "someValue");
      InternalCacheEntry third = InternalEntryFactory.create(MIRCEA, "value1");
      InternalCacheEntry forth = InternalEntryFactory.create(MANIK, "value2");
      cacheStore.store(first);
      cacheStore.store(second);
      cacheStore.store(third);
      cacheStore.store(forth);
      assertRowCounts(2, 2);
      Set<InternalCacheEntry> entries = cacheStore.loadAll();
      assert entries.size() == 4;
      assert entries.contains(first);
      assert entries.contains(second);
      assert entries.contains(third);
      assert entries.contains(forth);
   }

   public void testPurgeExpired() throws Exception {
      InternalCacheEntry first = InternalEntryFactory.create("String", "someValue", 1000);
      InternalCacheEntry second = InternalEntryFactory.create(MIRCEA, "value1", 1000);
      cacheStore.store(first);
      cacheStore.store(second);
      assertRowCounts(1, 1);
      Thread.sleep(1200);
      cacheStore.purgeExpired();
      assertRowCounts(0, 0);
   }

   public void testPurgeExpiredWithRemainingEntries() throws Exception {
      InternalCacheEntry first = InternalEntryFactory.create("String", "someValue", 1000);
      InternalCacheEntry second = InternalEntryFactory.create("String2", "someValue");
      InternalCacheEntry third = InternalEntryFactory.create(MIRCEA, "value1", 1000);
      InternalCacheEntry forth = InternalEntryFactory.create(MANIK, "value1");
      cacheStore.store(first);
      cacheStore.store(second);
      cacheStore.store(third);
      cacheStore.store(forth);
      assertRowCounts(2, 2);
      Thread.sleep(1200);
      cacheStore.purgeExpired();
      assertRowCounts(1, 1);
   }

   public void testTableConflict() {

   }

   private void assertRowCounts(int binary, int strings) {
      assertBinaryRowCount(binary);
      assertStringsRowCount(strings);
   }

   private void assertStringsRowCount(int rowCount) {

      JdbcMixedCacheStore store = (JdbcMixedCacheStore) cacheStore;
      ConnectionFactory connectionFactory = store.getConnectionFactory();
      String tableName = stringsTm.getTableName();
      int value = UnitTestDatabaseManager.rowCount(connectionFactory, tableName);
      assert value == rowCount : "Expected " + rowCount + " rows, actual value is " + value;

   }

   private void assertBinaryRowCount(int rowCount) {

      JdbcMixedCacheStore store = (JdbcMixedCacheStore) cacheStore;
      ConnectionFactory connectionFactory = store.getConnectionFactory();
      String tableName = binaryTm.getTableName();
      int value = UnitTestDatabaseManager.rowCount(connectionFactory, tableName);
      assert value == rowCount : "Expected " + rowCount + " rows, actual value is " + value;

   }


}
