package com.android.internal.telephony;

/** @hide */
interface ITelephony {
    void resetIms(int slotIndex);
    boolean isImsRegistered(int subId);
}
