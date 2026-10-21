package com.marketplace.backend.entity;

public enum ReturnReason {
    DAMAGED,           // hang bi hu hong / loi
    WRONG_ITEM,        // giao sai hang / sai phan loai
    NOT_AS_DESCRIBED,  // khong dung mo ta
    MISSING_ITEM,      // thieu hang
    FAKE,              // hang gia / nhai
    OTHER
}
