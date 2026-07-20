package kr.fintarget.api.domain.simulation.controller;
import jakarta.validation.Valid;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.simulation.dto.SimulationRequest;
import kr.fintarget.api.domain.simulation.dto.SimulationResponse;
import kr.fintarget.api.domain.simulation.service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/simulations")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    @PostMapping
    public ResponseEntity<ApiResponse<SimulationResponse>> runSimulation(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody SimulationRequest request) {
        return ResponseEntity.ok(
            ApiResponse.ok(simulationService.runSimulation(UUID.fromString(userId), request))
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SimulationResponse>>> getSimulations(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(
            ApiResponse.ok(simulationService.getSimulations(UUID.fromString(userId)))
        );
    }

    @GetMapping("/goal/{goalId}")
    public ResponseEntity<ApiResponse<List<SimulationResponse>>> getSimulationsByGoal(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID goalId) {
        return ResponseEntity.ok(
            ApiResponse.ok(simulationService.getSimulationsByGoal(UUID.fromString(userId), goalId))
        );
    }
}
