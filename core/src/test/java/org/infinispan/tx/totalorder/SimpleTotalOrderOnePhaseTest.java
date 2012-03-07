package org.infinispan.tx.totalorder;

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.interceptors.InterceptorChain;
import org.infinispan.interceptors.locking.OptimisticLockingInterceptor;
import org.infinispan.interceptors.locking.PessimisticLockingInterceptor;
import org.infinispan.interceptors.totalorder.TotalOrderInterceptor;
import org.infinispan.test.MultipleCacheManagersTest;
import org.infinispan.transaction.TransactionMode;
import org.infinispan.transaction.TransactionProtocol;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Mircea.Markus@jboss.com
 * @since 5.2.0
 */
@Test(groups = "functional", testName = "tx.totalorder.SimpleTotalOrderOnePhaseTest")
public class SimpleTotalOrderOnePhaseTest extends MultipleCacheManagersTest {

   protected int clusterSize = 2;

   @Override
   protected void createCacheManagers() throws Throwable {
      ConfigurationBuilder dcc = getDefaultClusteredCacheConfig(CacheMode.REPL_SYNC, true);
      dcc.transaction().transactionProtocol(TransactionProtocol.TOTAL_ORDER);
      createCluster(dcc, clusterSize);
      waitForClusterToForm();
   }

   public void testInteceptorChain() {
      InterceptorChain ic = advancedCache(0).getComponentRegistry().getComponent(InterceptorChain.class);
      assertTrue(ic.containsInterceptorType(TotalOrderInterceptor.class));
      assertFalse(ic.containsInterceptorType(OptimisticLockingInterceptor.class));
      assertFalse(ic.containsInterceptorType(PessimisticLockingInterceptor.class));
   }

   public void testToCacheIsTransactional() {
      assertTrue(cache(0).getCacheConfiguration().transaction().autoCommit());
      assertTrue(cache(0).getCacheConfiguration().transaction().transactionMode() == TransactionMode.TRANSACTIONAL);
   }

   public void testPut() {
      // test a simple put!
      assert cache(0).get("key") == null;
      assert cache(1).get("key") == null;

      cache(0).put("key", "value");


      assert cache(0).get("key").equals("value");
      assertEventuallyEqual(1, "key", "value");

      Map map = new HashMap();
      map.put("key2", "value2");
      map.put("key3", "value3");

      cache(0).putAll(map);

      assert cache(0).get("key").equals("value");
      assertEventuallyEqual(1, "key", "value");
      assert cache(0).get("key2").equals("value2");
      assertEventuallyEqual(1, "key2", "value2");
      assert cache(0).get("key3").equals("value3");
      assertEventuallyEqual(1, "key3", "value3");
   }

   public void removeTest() {
      cache(1).put("key", "value");
      assert cache(1).get("key").equals("value");
      assertEventuallyEqual(0, "key", "value");

      cache(0).remove("key");

      assert cache(0).get("key") == null;
      assertEventuallyEqual(1, "key", null);

      cache(0).put("key", "value");

      assert cache(0).get("key").equals("value");
      assertEventuallyEqual(1, "key", "value");

      cache(0).remove("key");

      assert cache(0).get("key") == null;
      assertEventuallyEqual(1, "key", null);
   }

   public void testPutIfAbsent() {
      cache(1).put("key", "valueOld");
      assert cache(0).get("key").equals("valueOld");
      assertEventuallyEqual(1, "key", "valueOld");

      cache(0).putIfAbsent("key", "value");

      assert cache(0).get("key").equals("valueOld");
      assert cache(1).get("key").equals("valueOld");

      cache(1).put("key", "value2");

      assertEventuallyEqual(0, "key", "value2");
      assert cache(1).get("key").equals("value2");

      cache(0).putIfAbsent("key", "value3");

      assert cache(0).get("key").equals("value2");
      assert cache(1).get("key").equals("value2");

      cache(0).putIfAbsent("key2", "value3");
      assert cache(0).get("key2").equals("value3");
      assertEventuallyEqual(1, "key2", "value3");
   }

   public void testRemoveIfPresent() {
      cache(0).put("key", "value1");
      cache(1).put("key", "value2");
      assert cache(0).get("key").equals("value2");
      assert cache(1).get("key").equals("value2");

      cache(0).remove("key", "value");

      assert cache(0).get("key").equals("value2") : "Should not remove";
      assert cache(1).get("key").equals("value2") : "Should not remove";

      cache(0).remove("key", "value2");

      assert cache(0).get("key") == null;
      assert cache(1).get("key") == null;
   }

   public void testClear() {
      cache(0).put("key", "value1");
      cache(1).put("key", "value2");
      assert cache(0).get("key").equals("value2");
      assertEventuallyEqual(1, "key", "value2");

      cache(0).clear();

      assert cache(0).get("key") == null;
      assert cache(1).get("key") == null;
   }

   public void testReplace() {

      cache(1).put("key", "value2");
      assertEventuallyEqual(0, "key", "value2");
      assert cache(1).get("key").equals("value2");

      Assert.assertEquals(cache(0).replace("key", "value1"), "value2");

      assertEquals(cache(0).get("key"), "value1");
      assertEventuallyEqual(1, "key", "value1");

      cache(0).put("key", "valueN");

      cache(0).replace("key", "valueN");

      assert cache(0).get("key").equals("valueN");
      assertEventuallyEqual(1, "key", "valueN");
   }

   public void testReplaceWithOldVal() {
      cache(1).put("key", "value2");
      assert cache(0).get("key").equals("value2");
      assertEventuallyEqual(1, "key", "value2");


      cache(0).put("key", "valueN");

      cache(0).replace("key", "valueOld", "value1");

      assert cache(0).get("key").equals("valueN");
      assertEventuallyEqual(1, "key", "valueN");

      cache(0).replace("key", "valueN", "value1");

      assert cache(0).get("key").equals("value1");
      assertEventuallyEqual(1, "key", "value1");
   }

   void assertEventuallyEqual(final int cacheIndex, final Object key, final Object value) {
      eventually(new Condition() {
         @Override
         public boolean isSatisfied() throws Exception {
            return value == null ? value == cache(cacheIndex).get(key) : value.equals(cache(cacheIndex).get(key));
         }
      });
   }
}
