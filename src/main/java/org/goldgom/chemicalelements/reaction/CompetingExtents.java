package org.goldgom.chemicalelements.reaction;
import java.math.*;
import java.util.*;
/** Deterministic integer extents. Small domains are exhaustive; large domains include pairwise
 * and triple stoichiometric vertices. This is a finite candidate model, not a global nonlinear equilibrium claim. */
public final class CompetingExtents {
 private CompetingExtents(){}
 public static List<BigInteger> candidates(CandidateSolver.Rule selected,BigInteger max,Map<String,BigInteger> inventory,List<CandidateSolver.Rule> competitors){
  TreeSet<BigInteger> values=new TreeSet<>();if(max.signum()<=0)return List.of();
  if(max.compareTo(BigInteger.valueOf(32))<=0){for(int i=1;i<=max.intValueExact();i++)values.add(BigInteger.valueOf(i));return List.copyOf(values);}
  values.add(max);
  for(var other:competitors){if(other.id().equals(selected.id()))continue;
   TreeSet<String> resources=new TreeSet<>(selected.reactants().keySet());resources.addAll(other.reactants().keySet());var names=new ArrayList<>(resources);
   for(int i=0;i<names.size();i++)for(int j=i+1;j<names.size();j++){
    String a=names.get(i),b=names.get(j);
    BigInteger ai=n(selected,a),aj=n(selected,b),bi=n(other,a),bj=n(other,b);
    BigInteger determinant=ai.multiply(bj).subtract(aj.multiply(bi));if(determinant.signum()==0)continue;
    BigInteger numerator=inventory.getOrDefault(a,BigInteger.ZERO).multiply(bj).subtract(inventory.getOrDefault(b,BigInteger.ZERO).multiply(bi));
    BigInteger x=numerator.divide(determinant);
    for(int offset=-1;offset<=1;offset++){BigInteger q=x.add(BigInteger.valueOf(offset));if(q.signum()>0&&q.compareTo(max)<=0)values.add(q);}
   }
  }
  // Exact Cramer's-rule projection of three-reaction resource vertices; no floating point.
  var others=competitors.stream().filter(r->!r.id().equals(selected.id())).sorted(Comparator.comparing(CandidateSolver.Rule::id)).toList();
  for(int u=0;u<others.size();u++)for(int v=u+1;v<others.size();v++){
   var b=others.get(u);var c=others.get(v);TreeSet<String> union=new TreeSet<>(selected.reactants().keySet());union.addAll(b.reactants().keySet());union.addAll(c.reactants().keySet());var names=new ArrayList<>(union);
   for(int i=0;i<names.size();i++)for(int j=i+1;j<names.size();j++)for(int k=j+1;k<names.size();k++){
    String[] rows={names.get(i),names.get(j),names.get(k)};BigInteger[][] matrix=new BigInteger[3][3];
    for(int r=0;r<3;r++){matrix[r][0]=n(selected,rows[r]);matrix[r][1]=n(b,rows[r]);matrix[r][2]=n(c,rows[r]);}
    BigInteger denominator=det(matrix);if(denominator.signum()==0)continue;
    for(int r=0;r<3;r++)matrix[r][0]=inventory.getOrDefault(rows[r],BigInteger.ZERO);
    BigInteger x=det(matrix).divide(denominator);
    for(int offset=-1;offset<=1;offset++){BigInteger q=x.add(BigInteger.valueOf(offset));if(q.signum()>0&&q.compareTo(max)<=0)values.add(q);}
   }
  }
  return List.copyOf(values);
 }
 private static BigInteger det(BigInteger[][] m){return m[0][0].multiply(m[1][1].multiply(m[2][2]).subtract(m[1][2].multiply(m[2][1])))
  .subtract(m[0][1].multiply(m[1][0].multiply(m[2][2]).subtract(m[1][2].multiply(m[2][0]))))
  .add(m[0][2].multiply(m[1][0].multiply(m[2][1]).subtract(m[1][1].multiply(m[2][0]))));}
 private static BigInteger n(CandidateSolver.Rule r,String s){return BigInteger.valueOf(r.reactants().getOrDefault(s,0));}
}
