package com.picsforyou.cloud.client.example;

import com.picsforyou.cloud.client.PicsForYouCloudClient;
import com.picsforyou.cloud.client.model.*;

import java.util.Base64;
import java.util.List;

public class QuickStart {

    public static void main(String[] args) throws Exception {
        var client = new PicsForYouCloudClient();

        // 1. Signup
        AuthResponse signup = client.signup("test@example.com", "mypassword");
        System.out.println("Signup: " + signup.getMessage());

        // 2. Login
        AuthResponse login = client.login("test@example.com", "mypassword");
        System.out.println("Login: " + login.getToken());
        System.out.println("Plan: " + login.getPlan());

        // 3. Get current user
        AuthResponse me = client.me();
        System.out.println("Storage used: " + me.getStorageUsedKb() + " KB");

        // 4. List plans
        List<Plan> plans = client.listPlans();
        plans.forEach(p -> System.out.println("  " + p.getName() + " €" + p.getPrice()));

        // 5. Select plan (free — no payment)
        PlanSelectionResult select = client.selectPlan("free");
        System.out.println("Plan select: " + select.getMessage());

        // 6. Upgrade to Base → requires payment
        PlanSelectionResult upgrade = client.selectPlan("base");
        if (upgrade.requiresUpgrade()) {
            System.out.println("Upgrade needed: €" + (upgrade.getDiffCents() / 100.0));
            // Redirect user to Stripe Checkout:
            PlanSelectionResult checkout = client.createCheckoutSession(login.getEmail(), "base", upgrade.getDiffCents());
            System.out.println("Stripe URL: " + checkout.getUrl());
        }

        // 7. Custom plan request
        var customResp = client.customPlanRequest("I need 100 GB for my video editing studio.");
        System.out.println("Custom request: " + customResp.get("message"));

        // 8. Upload a file (needs Bearer token)
        TokenResponse token = client.requestToken("dev-client-id", "dev-client-secret-xyz123");
        String img = Base64.getEncoder().encodeToString("fake-image-bytes".getBytes());
        UploadedFile uploaded = client.uploadFile(token.getAccessToken(), "test.txt", "text/plain", 15, img);
        System.out.println("Uploaded: " + uploaded.getId());

        // 9. List files
        List<UploadedFile> files = client.listFiles(token.getAccessToken());
        System.out.println("You have " + files.size() + " file(s)");

        // 10. Download a file
        if (!files.isEmpty()) {
            byte[] data = client.downloadFile(token.getAccessToken(), files.get(0).getId());
            System.out.println("Downloaded " + data.length + " bytes");
        }

        // 11. Delete a file
        if (!files.isEmpty()) {
            var deleted = client.deleteFile(token.getAccessToken(), files.get(0).getId());
            System.out.println("Deleted: " + deleted.get("message"));
        }

        // 12. Logout
        client.logout();
        System.out.println("Done.");
    }
}
