package org.goldgom.chemicalelements.network;

import java.math.*;
/** Presentation only. Never used for inventory serialization or settlement. */
public final class DisplayNumbers {
 private DisplayNumbers(){}
 public static String format(BigDecimal value,int digits){
  if(digits<1||digits>18)throw new IllegalArgumentException("precision 1..18");
  BigDecimal rounded=value.round(new MathContext(digits,RoundingMode.HALF_EVEN)).stripTrailingZeros();
  String text=rounded.toString();
  if(text.length()>128)throw new IllegalArgumentException("display exponent budget");
  return (rounded.compareTo(value)==0?"":"≈")+text;
 }
 public static String bounded(String value){return value.length()<=900?value:value.substring(0,880)+"… [text abbreviated]";}
}
