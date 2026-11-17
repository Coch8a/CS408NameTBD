package edu.boisestate.cs408.trenchroster.roster;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import edu.boisestate.cs408.trenchroster.auth.AccountDetailsService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/rosters")
public class RosterTransferController {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final RosterTransferService transfer;
    private final AccountDetailsService accounts;

    public RosterTransferController(RosterTransferService transfer, AccountDetailsService accounts) {
        this.transfer = transfer;
        this.accounts = accounts;
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<RosterTransferService.Snapshot> export(Principal principal, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(transfer.snapshot(accounts.currentAccount(principal.getName()).id(), id));
    }

    @PostMapping(value = "/import", consumes = "application/json")
    public ResponseEntity<?> importRoster(Principal principal, HttpServletRequest request) throws IOException {
        var input = request.getInputStream().readNBytes(RosterTransferService.MAX_FILE_BYTES + 1);
        if (input.length > RosterTransferService.MAX_FILE_BYTES) {
            return ResponseEntity.status(413).cacheControl(CacheControl.noStore())
                    .body(Map.of("message", "Choose a JSON file no larger than 1 MiB."));
        }
        try {
            var file = JSON.readTree(input);
            if (file == null || !file.isObject()) throw RosterTransferService.invalid("Choose a valid roster JSON file.");
            var roster = transfer.importFile(accounts.currentAccount(principal.getName()).id(), file);
            return ResponseEntity.created(URI.create("/api/rosters/" + roster.id()))
                    .cacheControl(CacheControl.noStore()).body(roster);
        } catch (JacksonException problem) {
            throw RosterTransferService.invalid("Choose a valid roster JSON file.");
        }
    }
}
