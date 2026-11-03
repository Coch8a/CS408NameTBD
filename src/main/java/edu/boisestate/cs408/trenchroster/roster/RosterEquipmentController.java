package edu.boisestate.cs408.trenchroster.roster;

import java.security.Principal;
import jakarta.validation.Valid;
import edu.boisestate.cs408.trenchroster.auth.AccountDetailsService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rosters/{rosterId}/units/{entryId}/equipment")
public class RosterEquipmentController {
    private final RosterEquipmentService equipment;
    private final AccountDetailsService accounts;

    public RosterEquipmentController(RosterEquipmentService equipment, AccountDetailsService accounts) {
        this.equipment = equipment;
        this.accounts = accounts;
    }

    @GetMapping
    public ResponseEntity<RosterEquipmentService.EquipmentView> view(Principal principal,
            @PathVariable long rosterId, @PathVariable long entryId) {
        return response(equipment.view(accounts.currentAccount(principal.getName()).id(), rosterId, entryId));
    }

    @PutMapping("/{equipmentId}")
    public ResponseEntity<RosterEquipmentService.EquipmentView> set(Principal principal,
            @PathVariable long rosterId, @PathVariable long entryId, @PathVariable long equipmentId,
            @Valid @RequestBody RosterEquipmentRequest request) {
        return response(equipment.set(accounts.currentAccount(principal.getName()).id(),
                rosterId, entryId, equipmentId, request.quantity()));
    }

    @DeleteMapping("/{equipmentId}")
    public ResponseEntity<RosterEquipmentService.EquipmentView> remove(Principal principal,
            @PathVariable long rosterId, @PathVariable long entryId, @PathVariable long equipmentId) {
        return response(equipment.set(accounts.currentAccount(principal.getName()).id(), rosterId, entryId, equipmentId, 0));
    }

    private ResponseEntity<RosterEquipmentService.EquipmentView> response(RosterEquipmentService.EquipmentView view) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(view);
    }
}
