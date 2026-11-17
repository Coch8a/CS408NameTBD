package edu.boisestate.cs408.trenchroster.roster;

import java.security.Principal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import edu.boisestate.cs408.trenchroster.auth.AccountDetailsService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rosters/{rosterId}/units")
public class RosterUnitController {
    private final RosterUnitService units;
    private final AccountDetailsService accounts;

    public RosterUnitController(RosterUnitService units, AccountDetailsService accounts) {
        this.units = units;
        this.accounts = accounts;
    }
    private long owner(Principal principal) { return accounts.currentAccount(principal.getName()).id(); }

    @GetMapping
    public ResponseEntity<RosterUnitService.UnitView> list(Principal principal, @PathVariable long rosterId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(units.view(owner(principal), rosterId));
    }
    @PostMapping
    public ResponseEntity<RosterUnitService.UnitView> add(Principal principal, @PathVariable long rosterId,
                                                         @Valid @RequestBody RosterUnitRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(units.add(owner(principal), rosterId, request.unitId()));
    }
    @PutMapping("/{entryId}/trench-mole")
    public ResponseEntity<RosterUnitService.UnitView> trenchMole(Principal principal, @PathVariable long rosterId,
            @PathVariable long entryId, @Valid @RequestBody TrenchMoleRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(units.setTrenchMole(owner(principal), rosterId, entryId, request.enabled()));
    }

    public record TrenchMoleRequest(@NotNull Boolean enabled) {}

    @DeleteMapping("/{entryId}")
    public ResponseEntity<RosterUnitService.UnitView> remove(Principal principal, @PathVariable long rosterId,
                                                            @PathVariable long entryId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(units.remove(owner(principal), rosterId, entryId));
    }
}
