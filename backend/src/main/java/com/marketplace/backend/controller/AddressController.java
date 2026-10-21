package com.marketplace.backend.controller;

import com.marketplace.backend.dto.request.AddressRequest;
import com.marketplace.backend.entity.Address;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.AddressRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.util.Provinces;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

// Buyer's address book ("Dia chi nhan hang"). Orders keep a text snapshot of
// the address, so editing or deleting one never changes past orders.
@RestController
@RequestMapping("/api/buyer/addresses")
@RequiredArgsConstructor
@Transactional
public class AddressController {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<Address>> myAddresses() {
        return ResponseEntity.ok(addressRepository.findByUserId(CurrentUser.id()).stream()
                .sorted(Comparator.comparing(Address::isDefault).reversed().thenComparing(Address::getId))
                .toList());
    }

    @PostMapping
    public ResponseEntity<Address> create(@Valid @RequestBody AddressRequest req) {
        if (!Provinces.isValid(req.getProvince())) {
            throw new ApiException("Vui lòng chọn tỉnh/thành phố", HttpStatus.BAD_REQUEST);
        }
        Long userId = CurrentUser.id();
        List<Address> existing = addressRepository.findByUserId(userId);
        // the first address is always the default one
        boolean makeDefault = req.isDefault() || existing.isEmpty();
        if (makeDefault) existing.forEach(a -> a.setDefault(false));

        User user = userRepository.getReferenceById(userId);
        Address address = Address.builder()
                .user(user)
                .recipientName(req.getRecipientName().trim())
                .phone(req.getPhone().trim())
                .addressLine(req.getAddressLine().trim())
                .ward(req.getWard())
                .district(req.getDistrict())
                .province(req.getProvince())
                .isDefault(makeDefault)
                .build();
        return ResponseEntity.ok(addressRepository.save(address));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<Void> makeDefault(@PathVariable Long id) {
        List<Address> mine = addressRepository.findByUserId(CurrentUser.id());
        if (mine.stream().noneMatch(a -> a.getId().equals(id))) {
            throw new ApiException("Địa chỉ không tồn tại", HttpStatus.NOT_FOUND);
        }
        mine.forEach(a -> a.setDefault(a.getId().equals(id)));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        List<Address> mine = addressRepository.findByUserId(CurrentUser.id());
        Address target = mine.stream().filter(a -> a.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ApiException("Địa chỉ không tồn tại", HttpStatus.NOT_FOUND));
        addressRepository.delete(target);
        // keep exactly one default while any address is left
        if (target.isDefault()) {
            mine.stream().filter(a -> !a.getId().equals(id)).findFirst().ifPresent(a -> a.setDefault(true));
        }
        return ResponseEntity.noContent().build();
    }
}
