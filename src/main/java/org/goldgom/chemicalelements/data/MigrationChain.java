package org.goldgom.chemicalelements.data;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.UnaryOperator;
/** Copy before migration: failed/newer schemas never become empty reconstructed inventories. */
public final class MigrationChain {
 private final int current; private final Map<Integer,UnaryOperator<JsonObject>> steps;
 public MigrationChain(int current,Map<Integer,UnaryOperator<JsonObject>> steps){if(current<1)throw new IllegalArgumentException();this.current=current;this.steps=Map.copyOf(steps);}
 public JsonObject migrate(JsonObject input){var result=input.deepCopy();int version=version(result);if(version<1||version>current)throw new IllegalArgumentException("unsupported schema "+version);while(version<current){var step=steps.get(version);if(step==null)throw new IllegalArgumentException("missing migration "+version);result=Objects.requireNonNull(step.apply(result)).deepCopy();if(version(result)!=version+1)throw new IllegalArgumentException("migration must advance one version");version++;}return result;}
 private static int version(JsonObject root){var e=root.get("schema_version");if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("schema_version must be integer number");var text=e.getAsString();if(text.length()>10||!text.matches("[1-9][0-9]*"))throw new IllegalArgumentException("invalid schema_version");try{return Integer.parseInt(text);}catch(NumberFormatException ex){throw new IllegalArgumentException("schema_version overflow",ex);}}
}