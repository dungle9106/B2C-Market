package com.marketplace.backend.entity;

// why a product's stock moved (InventoryLog)
public enum InventoryReason {
    CREATE,     // initial stock of a new product
    ORDER,      // sold: an order was placed
    CANCEL,     // an order was cancelled, items back in stock
    RETURN,     // an approved return/refund, items back in stock
    EDIT,       // the seller changed the stock in the product form / product list
    SOLD_OUT,   // "Danh dau het hang"
    IMPORT,     // created by the Excel import
    RESTOCK,    // stock adjustment: goods received ("Nhap hang")
    STOCKTAKE,  // stock adjustment: counted, corrected to the real quantity ("Kiem kho")
    DAMAGED,    // stock adjustment: broken / lost goods taken out
    OTHER
}
