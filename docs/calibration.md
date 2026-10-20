# Calibración

## Escala de potencia del medidor (2026-10-19, de noche)

Lectura en vivo del DTU (~22:15, sin producción):

- phase_total_power = -155, phase_A_power = -70, phase_B_power = -85
- voltage_phase_A = 12380 (centésimas de voltio, 123,80 V), voltage_phase_B = 12210 (122,10 V)
- current_phase_A = 612 (centésimas de amperio, 6,12 A), current_phase_B = 765 (7,65 A)
- power_factor_phase_A = -928, power_factor_phase_B = -914 (milésimas)
- V*I*|PF|: fase A = 123,8 x 6,12 x 0,928 = 703 W; fase B = 122,1 x 7,65 x 0,914 = 854 W; suma = 1557 W, es decir 155 x 10 W.
- El usuario confirma unos 1,5 kW de consumo real en ese momento.

Conclusión: la potencia del medidor es el valor crudo x 10 W (`meterPowerWattsPerUnit = 10.0`). El signo negativo indica importación de la red (de noche la casa consume de la red), por lo que `importIsPositive = false` sigue siendo la hipótesis vigente; se confirma de día en la Tarea 8.

## Referencia (app Hoymiles)

## Unidades de producción (dtuPowerWattsPerUnit, dailyEnergyWhPerUnit)

## Signo del medidor (importIsPositive)

## Páginas que traen cada dato

## Protector de pantalla en esta TV

Funciona: el protector de SolarDream se activa con `am start -n com.android.systemui/.Somnambulator` tras fijar `screensaver_components`; no hizo falta la actividad de respaldo.
