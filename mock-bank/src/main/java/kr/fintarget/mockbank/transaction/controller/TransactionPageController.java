package kr.fintarget.mockbank.transaction.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kr.fintarget.mockbank.transaction.ExpenseCategory;
import kr.fintarget.mockbank.transaction.dto.TransactionForm;
import kr.fintarget.mockbank.transaction.entity.Transaction;
import kr.fintarget.mockbank.transaction.service.DemoDataService;
import kr.fintarget.mockbank.transaction.service.TransactionService;
import kr.fintarget.mockbank.webhook.DeliveryResult;
import kr.fintarget.mockbank.webhook.WebhookDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * 거래는 먼저 저장(커밋)한 뒤 핀타겟으로 보낸다. 전송에 실패해도 거래는 남고, 재전송 버튼으로 다시 보낼 수 있다.
 */
@Controller
@RequiredArgsConstructor
public class TransactionPageController {

    private final TransactionService transactionService;
    private final DemoDataService demoDataService;
    private final WebhookDeliveryService webhookDeliveryService;
    private final Clock clock;

    @GetMapping("/")
    public String index(@RequestParam(required = false) String userId, Model model) {
        if (!model.containsAttribute("form")) {
            TransactionForm form = new TransactionForm();
            form.setUserId(userId);
            form.setCategory(ExpenseCategory.FOOD.label());
            form.setSpentAt(LocalDate.now(clock));
            model.addAttribute("form", form);
        }
        return renderIndex(model);
    }

    @PostMapping("/transactions")
    public String createTransaction(@Valid @ModelAttribute("form") TransactionForm form, BindingResult bindingResult,
                                    Model model, RedirectAttributes redirectAttributes, HttpServletResponse response) {
        if (form.getCategory() != null && !form.getCategory().isBlank()
                && ExpenseCategory.fromLabel(form.getCategory()).isEmpty()) {
            bindingResult.rejectValue("category", "invalid", "지원하지 않는 카테고리입니다.");
        }
        if (form.getSpentAt() != null && form.getSpentAt().isAfter(LocalDate.now(clock))) {
            bindingResult.rejectValue("spentAt", "future", "결제 날짜는 오늘 이후일 수 없습니다.");
        }
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return renderIndex(model);
        }

        UUID userId = UUID.fromString(form.getUserId().trim());
        Transaction transaction = transactionService.create(userId, form.getAmount(), form.getCategory(), form.getDescription(), form.getSpentAt());
        String message = String.format("결제가 발생했습니다. (%s %,d원)", form.getCategory(), form.getAmount());
        deliverAndFlash(List.of(transaction), message, redirectAttributes);
        return redirectToIndex(userId, redirectAttributes);
    }

    @PostMapping("/demo/baseline")
    public String generateBaseline(@RequestParam(required = false) String userId, RedirectAttributes redirectAttributes) {
        return runDemo(userId, redirectAttributes, id -> {
            List<Transaction> created = demoDataService.generateBaseline(id);
            return new DemoOutcome(created, String.format("기준선 거래 %d건을 생성했습니다. (지난 %d개월 × 모든 카테고리)",
                    created.size(), DemoDataService.BASELINE_MONTHS));
        });
    }

    @PostMapping("/demo/random")
    public String generateRandom(@RequestParam(required = false) String userId, RedirectAttributes redirectAttributes) {
        return runDemo(userId, redirectAttributes, id -> {
            List<Transaction> created = demoDataService.generateRandom(id);
            return new DemoOutcome(created, String.format("이번 달 랜덤 거래 %d건을 생성했습니다.", created.size()));
        });
    }

    @PostMapping("/demo/food-surge")
    public String generateFoodSurge(@RequestParam(required = false) String userId, RedirectAttributes redirectAttributes) {
        return runDemo(userId, redirectAttributes, id -> {
            DemoDataService.SurgeResult result = demoDataService.generateFoodSurge(id);
            if (result.createdCount() == 0) {
                return new DemoOutcome(List.of(), String.format("이번 달 식비(%,d원)가 이미 월평균(%,d원)의 2배 이상이라 새로 생성하지 않았습니다.",
                        result.currentMonthTotal(), result.pastMonthlyAverage()));
            }
            return new DemoOutcome(result.created(), String.format("식비 거래 %d건을 생성했습니다. 이번 달 식비 %,d원 (이전 월평균 %,d원)",
                    result.createdCount(), result.currentMonthTotal(), result.pastMonthlyAverage()));
        });
    }

    @PostMapping("/webhooks/resend")
    public String resendUnsent(@RequestParam(required = false) String userId, RedirectAttributes redirectAttributes) {
        DeliveryResult result = webhookDeliveryService.resendUnsent();
        if (result.total() == 0) {
            redirectAttributes.addFlashAttribute("message", "다시 보낼 미전송·실패 거래가 없습니다.");
        } else {
            flashDelivery(result, "미전송·실패 거래를 다시 보냈습니다.", redirectAttributes);
        }
        parseUserId(userId).ifPresent(id -> redirectAttributes.addAttribute("userId", id.toString()));
        return "redirect:/";
    }

    private record DemoOutcome(List<Transaction> created, String message) {
    }

    private String runDemo(String rawUserId, RedirectAttributes redirectAttributes, Function<UUID, DemoOutcome> action) {
        Optional<UUID> userId = parseUserId(rawUserId);
        if (userId.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "시연 데이터를 만들려면 올바른 핀타겟 사용자 UUID를 입력해주세요.");
            redirectAttributes.addAttribute("userId", rawUserId);
            return "redirect:/";
        }
        try {
            DemoOutcome outcome = action.apply(userId.get());
            deliverAndFlash(outcome.created(), outcome.message(), redirectAttributes);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectToIndex(userId.get(), redirectAttributes);
    }

    private void deliverAndFlash(List<Transaction> transactions, String message, RedirectAttributes redirectAttributes) {
        if (transactions.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", message);
            return;
        }
        flashDelivery(webhookDeliveryService.deliver(transactions), message, redirectAttributes);
    }

    private void flashDelivery(DeliveryResult result, String message, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("message", message + " · " + result.summary());
        if (result.hasFailure()) {
            redirectAttributes.addFlashAttribute("error", "핀타겟 전송 실패 " + result.failed() + "건: " + result.errorMessage()
                    + " (거래는 저장되어 있으니 원인을 해결한 뒤 재전송해 주세요.)");
        }
    }

    private Optional<UUID> parseUserId(String rawUserId) {
        if (rawUserId == null || !rawUserId.matches(TransactionForm.UUID_REGEX)) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(rawUserId.trim()));
    }

    private String redirectToIndex(UUID userId, RedirectAttributes redirectAttributes) {
        redirectAttributes.addAttribute("userId", userId.toString());
        return "redirect:/";
    }

    private String renderIndex(Model model) {
        model.addAttribute("categories", ExpenseCategory.labels());
        model.addAttribute("transactions", transactionService.getRecentTransactions());
        model.addAttribute("unsentCount", transactionService.countUnsentTransactions());
        model.addAttribute("today", LocalDate.now(clock));
        return "index";
    }
}
