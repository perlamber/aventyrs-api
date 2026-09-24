package org.aventyrs.api.campaign;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.aventyrs.api.campaign.dto.BagAddRequest;
import org.aventyrs.api.campaign.dto.BagClaimRequest;
import org.aventyrs.api.campaign.dto.BagDepositRequest;
import org.aventyrs.api.campaign.dto.BagLootRequest;
import org.aventyrs.api.campaign.dto.CampaignCreateRequest;
import org.aventyrs.api.campaign.dto.CampaignResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Campanhas, their Sessões and their participants. Every Sessão or participant change re-broadcasts
 * the whole {@link CampaignResponse} on {@code /topic/campaigns/{id}/sessions}, so a client showing
 * one of the Campanha's sheets can lock or unlock progression without reloading. Every bag change
 * broadcasts it on {@code /topic/campaigns/{id}/bag} instead.
 */
@RestController
@RequestMapping("/api/campaigns")
@Tag(name = "Campaigns")
public class CampaignController {

    private final CampaignService service;
    private final SimpMessagingTemplate messagingTemplate;

    public CampaignController(CampaignService service, SimpMessagingTemplate messagingTemplate) {
        this.service = service;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping
    public ResponseEntity<CampaignResponse> create(@Valid @RequestBody CampaignCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{id}")
    public CampaignResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping
    public List<CampaignResponse> list() {
        return service.list();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Adds the next numbered Sessão in {@code CREATED}. A {@code 409} while another is not yet ENDED. */
    @PostMapping("/{id}/sessions")
    public ResponseEntity<CampaignResponse> createSession(@PathVariable String id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(broadcast(service.createSession(id)));
    }

    /** CREATED → ONGOING, which locks progression. A {@code 409} if another Sessão is ONGOING or this one isn't CREATED. */
    @PostMapping("/{id}/sessions/{number}/start")
    public CampaignResponse startSession(@PathVariable String id, @PathVariable int number) {
        return broadcast(service.startSession(id, number));
    }

    /** ONGOING → ENDED, which unlocks progression. A {@code 409} if this Sessão isn't ONGOING. */
    @PostMapping("/{id}/sessions/{number}/end")
    public CampaignResponse endSession(@PathVariable String id, @PathVariable int number) {
        return broadcast(service.endSession(id, number));
    }

    /** Makes the sheet a participant, moving it out of any other Campanha, since a sheet has only one. */
    @PutMapping("/{id}/participants/{characterSheetId}")
    public CampaignResponse addParticipant(@PathVariable String id, @PathVariable String characterSheetId) {
        return broadcast(service.addParticipant(id, characterSheetId));
    }

    @DeleteMapping("/{id}/participants/{characterSheetId}")
    public CampaignResponse removeParticipant(@PathVariable String id, @PathVariable String characterSheetId) {
        return broadcast(service.removeParticipant(id, characterSheetId));
    }

    /** The GM puts an item into the Campanha's bag. */
    @PostMapping("/{id}/bag")
    public ResponseEntity<CampaignResponse> addToBag(@PathVariable String id, @Valid @RequestBody BagAddRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(broadcastBag(service.addToBag(id, request)));
    }

    /** The GM throws a bag item away. A {@code 409 BAG_ITEM_NOT_FOUND} if it is already gone. */
    @DeleteMapping("/{id}/bag/{entryId}")
    public CampaignResponse discardFromBag(@PathVariable String id, @PathVariable String entryId) {
        return broadcastBag(service.discardFromBag(id, entryId));
    }

    /** A participant takes a bag item. A {@code 409 BAG_ITEM_NOT_FOUND} if someone took it first. */
    @PostMapping("/{id}/bag/{entryId}/claim")
    public CampaignResponse claimFromBag(@PathVariable String id, @PathVariable String entryId,
            @Valid @RequestBody BagClaimRequest request) {
        return broadcastBag(service.claimFromBag(id, entryId, request));
    }

    /** A participant puts one carried item into the bag. */
    @PostMapping("/{id}/bag/deposit")
    public CampaignResponse depositToBag(@PathVariable String id, @Valid @RequestBody BagDepositRequest request) {
        return broadcastBag(service.depositToBag(id, request));
    }

    /** Saquear, or the end-of-combat sweep: everything the foe carries goes into the bag. */
    @PostMapping("/{id}/bag/loot")
    public CampaignResponse lootIntoBag(@PathVariable String id, @Valid @RequestBody BagLootRequest request) {
        return broadcastBag(service.lootIntoBag(id, request));
    }

    private CampaignResponse broadcastBag(CampaignResponse campaign) {
        messagingTemplate.convertAndSend("/topic/campaigns/" + campaign.id() + "/bag", campaign);
        return campaign;
    }

    private CampaignResponse broadcast(CampaignResponse campaign) {
        messagingTemplate.convertAndSend("/topic/campaigns/" + campaign.id() + "/sessions", campaign);
        return campaign;
    }
}
