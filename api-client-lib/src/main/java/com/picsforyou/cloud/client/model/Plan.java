package com.picsforyou.cloud.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Plan {

    private String id;
    private String name;
    private double price;
    @JsonProperty("storageMb") private long storageLimitMb;
    private String description;

    public Plan() {}

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

    public boolean isPaid() { return price > 0; }

    @Override
    public String toString() {
        return "Plan{id='" + id + "', name='" + name + "', price=" + price + "}";
    }
}
