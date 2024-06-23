package com.spop.poverlay.sensor.v1

enum class Command(val id: Int) {
    GetRpmRepeating(1),
    GetPowerRepeating(2),
    GetResistanceRepeating(3)
}