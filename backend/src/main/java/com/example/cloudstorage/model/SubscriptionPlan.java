package com.example.cloudstorage.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private double price;

    @Column(nullable = false)
    private long storageLimitMb;

    @Column(length = 512)
    private String description;

    public SubscriptionPlan() {}

    public SubscriptionPlan(String id, String name, double price, long storageLimitMb, String description) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.storageLimitMb = storageLimitMb;
        this.description = description;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public long getStorageLimitMb() { return storageLimitMb; }
    public void setStorageLimitMb(long storageLimitMb) { this.storageLimitMb = storageLimitMb; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
