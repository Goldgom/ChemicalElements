package org.goldgom.chemicalelements.network;

/** Pure policy shared by the real packet path and executable adversarial tests. */
public final class QueryPolicy {
 private QueryPolicy(){}
 public static boolean authorized(int currentContainer,int requestedContainer,int slots,int slot,boolean valid){return valid&&currentContainer==requestedContainer&&slot>=0&&slot<slots;}
 public static boolean pageValid(int page,int size,int count){if(page<0||size<=0||count<0)return false;long start=(long)page*size;return start<count||start==0&&count==0;}
 public static boolean revisionValid(long requested,long actual){return requested==-1||requested==actual;}
 public static boolean throttle(Long previous,long now){return previous==null||now>=previous&&now-previous>=4;}
}
