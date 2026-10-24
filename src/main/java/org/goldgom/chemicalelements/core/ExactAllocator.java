package org.goldgom.chemicalelements.core;
import java.math.BigInteger;
import java.util.*;
import org.goldgom.chemicalelements.api.chem.MolAmount;
/** Largest remainder apportionment. Equal remainders use original destination index. */
public final class ExactAllocator {
 private ExactAllocator(){}
 public static List<MolAmount> allocate(MolAmount amount,List<BigInteger> weights){
 if(weights.isEmpty()||weights.stream().anyMatch(w->w.signum()<0))throw new IllegalArgumentException("invalid weights");
 var sum=weights.stream().reduce(BigInteger.ZERO,BigInteger::add);if(sum.signum()==0)throw new IllegalArgumentException("zero weight sum");
 var q=new ArrayList<BigInteger>();var r=new ArrayList<BigInteger>();var used=BigInteger.ZERO;
 for(var w:weights){var qr=amount.quanta().multiply(w).divideAndRemainder(sum);q.add(qr[0]);r.add(qr[1]);used=used.add(qr[0]);}
 var order=new ArrayList<Integer>();for(int i=0;i<weights.size();i++)order.add(i);order.sort((a,b)->{int c=r.get(b).compareTo(r.get(a));return c==0?Integer.compare(a,b):c;});
 int left=amount.quanta().subtract(used).intValueExact();for(int i=0;i<left;i++){int k=order.get(i);q.set(k,q.get(k).add(BigInteger.ONE));}return q.stream().map(MolAmount::new).toList();
 }
}