package org.goldgom.chemicalelements.api.instance;
public record TransferContext(String reason,boolean simulation,long expectedSourceRevision,long expectedTargetRevision) {}