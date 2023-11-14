package com.example.bluetoothchat;

import java.io.Serializable;

public class User implements Serializable {
    private String name;
    private String desciption;

    public User(String name, String desciption) {
        this.name = name;
        this.desciption = desciption;
    }

    public User() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDesciption() {
        return desciption;
    }

    public void setDesciption(String desciption) {
        this.desciption = desciption;
    }
}
