package edu.boisestate.cs408.trenchroster.roster;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import jakarta.validation.Valid;
import edu.boisestate.cs408.trenchroster.auth.AccountDetailsService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Session-protected roster API; writes also pass through Spring Security's CSRF filter. */
@RestController
@RequestMapping("/api/rosters")
public class RosterController {
    private final RosterService rosters;
    private final AccountDetailsService accounts;
    public RosterController(RosterService rosters, AccountDetailsService accounts) {
        this.rosters = rosters;
        this.accounts = accounts;
    }
    private long owner(Principal principal) { return accounts.currentAccount(principal.getName()).id(); }

    @GetMapping
    public ResponseEntity<List<RosterService.Roster>> list(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rosters.list(owner(principal)));
    }
    @GetMapping("/factions")
    public ResponseEntity<List<RosterService.Faction>> factions(Principal principal) {
        owner(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rosters.factions());
    }
    @GetMapping("/{id}")
    public ResponseEntity<RosterService.Roster> get(Principal principal, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rosters.get(owner(principal), id));
    }
    @PostMapping
    public ResponseEntity<RosterService.Roster> create(Principal principal, @Valid @RequestBody RosterRequest request) {
        var roster = rosters.create(owner(principal), request);
        return ResponseEntity.created(URI.create("/api/rosters/" + roster.id()))
                .cacheControl(CacheControl.noStore()).body(roster);
    }
    @PutMapping("/{id}")
    public ResponseEntity<RosterService.Roster> update(Principal principal, @PathVariable long id,
                                                     @Valid @RequestBody RosterRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rosters.update(owner(principal), id, request));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Principal principal, @PathVariable long id) {
        rosters.delete(owner(principal), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
