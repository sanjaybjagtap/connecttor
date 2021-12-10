// Copyright (c) 2004 Financial Fusion, Inc.  All Rights Reserved.

package com.ffusion.ffs.bpw.fulfill.checkfree;

/**
 * This class provides a data-structure to hold a CheckFree
 * record object (TypeSubInfo, TypePayeeInfo, TypePmtInfo,
 * etc.) and a Throwable object that is associated with the
 * CheckFree record object.
 *
 * This class is intended to be used for handling error
 * cases during CheckFree SIS file generation.
 */
public class SISGenError {

    public Object record;       // CheckFree record object (TypeSubInfo,
                                // TypePayeeInfo, TypePayeeAcctInfo, etc.).

    public Throwable error;     // The thrown object associated with the record.

    public SISGenError(Object rec, Throwable t)
    {
        record = rec;
        error = t;
    }// SISGenErrorHolder()
}
