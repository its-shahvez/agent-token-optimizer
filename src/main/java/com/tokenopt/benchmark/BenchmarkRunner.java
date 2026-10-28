package com.tokenopt.benchmark;

import com.tokenopt.budget.TokenBudgetManager;
import com.tokenopt.core.CompressionResult;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class BenchmarkRunner implements CommandLineRunner {

    private final TokenBudgetManager budgetManager;

    public BenchmarkRunner(TokenBudgetManager budgetManager) {
        this.budgetManager = budgetManager;
    }

    @Override
    public void run(String... args) {
        System.out.println("\n================================================================================");
        System.out.println("🚀 AGENT TOKEN OPTIMIZER (JAVA) - LIVE BENCHMARK SUITE");
        System.out.println("================================================================================");

        String syntheticLogs = generateSyntheticLogs(5000);
        String syntheticStack = generateSyntheticStackTrace();
        String syntheticCode = generateSyntheticJavaCode(40);

        System.out.println("| Workload Type          | Original Tokens | Compressed Tokens | Token Savings | Latency (ms) | Strategy Used |");
        System.out.println("|------------------------|-----------------|-------------------|---------------|--------------|---------------|");

        CompressionResult logRes = runBench("5,000-Line Prod Logs", syntheticLogs, "LOG");
        CompressionResult stackRes = runBench("Spring Boot StackTrace", syntheticStack, "STACK_TRACE");
        CompressionResult codeRes = runBench("40-Method Java Class", syntheticCode, "CODE");

        System.out.println("================================================================================");
        System.out.println("\n🔍 SAMPLE COMPRESSED STACK TRACE PREVIEW (chunkId=" + stackRes.chunkId() + "):");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(stackRes.compressedContent());
        System.out.println("================================================================================\n");
    }

    private CompressionResult runBench(String name, String payload, String type) {
        long start = System.nanoTime();
        CompressionResult res = budgetManager.optimize(payload, type, null);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("| %-22s | %,15d | %,17d | %12.1f%% | %10d ms | %-13s |%n",
                name,
                res.originalTokens(),
                res.compressedTokens(),
                res.reductionPercentage(),
                elapsedMs,
                res.strategyUsed());
        return res;
    }

    private String generateSyntheticLogs(int lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
            if (i == 2450) {
                sb.append("2026-10-28T14:22:10.512Z ERROR [OrderService] Failed to charge card for tx=")
                        .append(UUID.randomUUID()).append(" reason=INSUFFICIENT_FUNDS\n");
            } else if (i % 3 == 0) {
                sb.append("2026-10-28T14:20:01.102Z INFO  [HikariPool-1] Connection acquired session=")
                        .append(UUID.randomUUID()).append(" latency=").append(1000 + i).append("ms\n");
            } else {
                sb.append("2026-10-28T14:20:01.105Z DEBUG [JwtAuthFilter] Validated bearer token for requestId=")
                        .append(UUID.randomUUID()).append(" userId=").append(50000 + (i % 50)).append("\n");
            }
        }
        return sb.toString();
    }

    private String generateSyntheticStackTrace() {
        StringBuilder sb = new StringBuilder();
        sb.append("org.springframework.web.util.NestedServletException: Request processing failed: java.lang.IllegalStateException: Payment Gateway Timeout\n");
        sb.append("\tat com.tokenopt.order.OrderController.checkout(OrderController.java:84)\n");
        sb.append("\tat com.tokenopt.order.OrderService.processOrder(OrderService.java:142)\n");
        for (int i = 0; i < 35; i++) {
            sb.append("\tat org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:186)\n");
            sb.append("\tat org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)\n");
            sb.append("\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:178)\n");
            sb.append("\tat java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)\n");
        }
        sb.append("Caused by: java.net.SocketTimeoutException: Read timed out after 30000ms\n");
        sb.append("\tat com.tokenopt.payment.StripeGatewayClient.charge(StripeGatewayClient.java:67)\n");
        sb.append("\tat com.tokenopt.order.OrderService.processOrder(OrderService.java:139)\n");
        for (int i = 0; i < 20; i++) {
            sb.append("\tat org.springframework.cglib.proxy.MethodProxy.invoke(MethodProxy.java:218)\n");
        }
        return sb.toString();
    }

    private String generateSyntheticJavaCode(int methods) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.example.service;\n\n");
        sb.append("import java.util.List;\nimport java.util.Map;\nimport org.springframework.stereotype.Service;\n\n");
        sb.append("@Service\npublic class EnterpriseBillingService {\n\n");
        sb.append("    private final String merchantId = \"MRC_991\";\n\n");
        for (int i = 1; i <= methods; i++) {
            sb.append("    public String calculateInvoiceTier").append(i).append("(String customerId, double baseAmount) {\n");
            sb.append("        double tax = baseAmount * 0.18;\n");
            sb.append("        double discount = baseAmount > 1000 ? baseAmount * 0.05 : 0.0;\n");
            sb.append("        double finalTotal = baseAmount + tax - discount;\n");
            sb.append("        System.out.println(\"Calculated total for \" + customerId + \": \" + finalTotal);\n");
            sb.append("        return String.format(\"INV-%s-%.2f\", customerId, finalTotal);\n");
            sb.append("    }\n\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}