# Viltrumite Boss (Fabric 1.20.1)

## Сохтан
1. JDK 17 + Gradle 8.5 (ё лоиҳаро дар IntelliJ IDEA кушоед).
2. `gradle build`
3. Файли `build/libs/viltrumite-boss-1.0.0.jar` -> папкаи `mods`.
4. Дар `mods` Fabric API 0.92.2+1.20.1 ҳам бояд бошад.

## Истифода
- Дар Creative: Spawn Egg "Viltrumite" ё `/summon viltrumite_boss:viltrumite`
- Босс ба бозингарони Creative ҳамла намекунад -> `/gamemode survival`
- Ҳамаи рақамҳо (ҷон, зарар, суръат, эҳтимол) дар ҳамоначи боло дар
  `entity/ViltrumiteEntity.java` ("ТАНЗИМОТ"), гапҳо дар `Taunts.java`.
