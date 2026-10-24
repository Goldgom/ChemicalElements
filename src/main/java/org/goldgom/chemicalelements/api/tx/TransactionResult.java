package org.goldgom.chemicalelements.api.tx;
public record TransactionResult(boolean committed,String code,String detail) {
 public static TransactionResult rejected(String code,String detail){return new TransactionResult(false,code,detail);}
 public static TransactionResult success(){return new TransactionResult(true,"committed","");}
}