package org.goldgom.chemicalelements.gametest.machine;
import org.goldgom.chemicalelements.network.QueryPolicy;
public final class QueryPolicyTest {
 private static int assertions;
 private static void check(boolean b){assertions++;if(!b)throw new AssertionError("assertion "+assertions);}
 public static void main(String[] args){
  check(QueryPolicy.authorized(5,5,36,0,true));check(QueryPolicy.authorized(5,5,36,35,true));
  check(!QueryPolicy.authorized(5,4,36,0,true));check(!QueryPolicy.authorized(5,5,36,-1,true));check(!QueryPolicy.authorized(5,5,36,36,true));check(!QueryPolicy.authorized(5,5,36,0,false));
  check(QueryPolicy.pageValid(0,8,0));check(QueryPolicy.pageValid(7,8,64));check(!QueryPolicy.pageValid(8,8,64));check(!QueryPolicy.pageValid(Integer.MAX_VALUE,8,64));check(!QueryPolicy.pageValid(-1,8,64));
  check(QueryPolicy.revisionValid(-1,25));check(QueryPolicy.revisionValid(25,25));check(!QueryPolicy.revisionValid(24,25));check(!QueryPolicy.revisionValid(-2,25));
  check(QueryPolicy.throttle(null,0));check(QueryPolicy.throttle(0L,4));check(!QueryPolicy.throttle(0L,3));check(!QueryPolicy.throttle(10L,9));
  for(int page=0;page<10000;page++)check(QueryPolicy.pageValid(page,8,64)==(page<8));
  System.out.println(assertions+" network policy assertions passed");
 }
}
