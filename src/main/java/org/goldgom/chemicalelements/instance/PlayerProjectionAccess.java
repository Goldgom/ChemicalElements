package org.goldgom.chemicalelements.instance;

/** Attached to the actual Player by a required mixin. Not a container or material store. */
public interface PlayerProjectionAccess {
    PlayerProjectionState chemicalelements$projectionState();
    void chemicalelements$beginMaterialSerialization(org.goldgom.chemicalelements.instance.materialtx.ReservedPlayerSerialization capability);
    void chemicalelements$endMaterialSerialization(org.goldgom.chemicalelements.instance.materialtx.ReservedPlayerSerialization capability);
}
