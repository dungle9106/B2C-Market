package com.marketplace.backend.dto.response;

import com.marketplace.backend.entity.BusinessType;
import com.marketplace.backend.entity.ShopVerification;
import com.marketplace.backend.entity.VerificationStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class VerificationResponse {
    private Long id;
    private Long shopId;
    private String shopName;
    private String sellerEmail;     // admin review screen
    private BusinessType businessType;
    private String legalName;
    private String idNumber;        // masked unless forAdmin
    private String taxCode;
    private String licenseNumber;
    private String businessAddress;
    private List<String> documentUrls;
    private VerificationStatus status;
    private String rejectReason;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;

    public static VerificationResponse from(ShopVerification v, boolean forAdmin) {
        VerificationResponse r = new VerificationResponse();
        r.setId(v.getId());
        r.setShopId(v.getShop().getId());
        r.setShopName(v.getShop().getName());
        if (forAdmin) r.setSellerEmail(v.getShop().getSeller().getEmail());
        r.setBusinessType(v.getBusinessType());
        r.setLegalName(v.getLegalName());
        // the seller already knows their number; showing only the tail limits what leaks from a shared screen
        r.setIdNumber(forAdmin ? v.getIdNumber() : "********" + v.getIdNumber().substring(8));
        r.setTaxCode(v.getTaxCode());
        r.setLicenseNumber(v.getLicenseNumber());
        r.setBusinessAddress(v.getBusinessAddress());
        r.setDocumentUrls(List.copyOf(v.getDocumentUrls()));
        r.setStatus(v.getStatus());
        r.setRejectReason(v.getRejectReason());
        r.setSubmittedAt(v.getSubmittedAt());
        r.setReviewedAt(v.getReviewedAt());
        return r;
    }
}
