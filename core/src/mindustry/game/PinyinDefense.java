package mindustry.game;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;

/** Fixed-step tower defense combat, shared by the live scene and balance simulations. Seconds, not ticks. */
public class PinyinDefense{
    public static final int waves = 20, maxEnemies = 100;
    public static final float resourceInterval = 5f, constructionInterval = 5f, waveInterval = 23f, baseMax = 1000f;
    /** Presentation hooks fire once at the actual combat event, never from rendering. */
    public interface Feedback{
        default void fired(Shot shot){}
        default void impacted(Shot shot){}
        default void destroyed(Enemy enemy){}
        default void destroyed(Tower tower){}
        default void shieldBroken(Enemy enemy){}
    }
    public enum TowerKind{
        rapid("连射机枪", "快速点射", 145, 0.38f, 8),
        scatter("扇形霰弹", "同时击中三名敌人", 125, 1.2f, 17),
        artillery("抛物榴炮", "延迟爆炸、范围伤害", 200, 2f, 30),
        missile("追踪导弹", "追踪双发", 185, 1.65f, 23),
        laser("贯穿激光", "直线贯穿、无视装甲", 175, 1.7f, 25),
        lightning("连锁电塔", "弹射四个目标", 145, 1.6f, 23),
        frost("寒霜喷射", "范围减速", 140, 1f, 13),
        flame("烈焰喷射", "持续灼烧", 125, 0.8f, 14),
        poison("毒雾发射", "范围中毒、抑制再生", 170, 1.7f, 21),
        rail("电磁狙击", "远程穿甲、优先重甲", 250, 2.8f, 62),
        fragment("分裂榴弹", "命中后向周围散射", 165, 1.8f, 25),
        boomerang("回旋刃塔", "去程回程两次切割", 160, 1.5f, 23),
        vortex("引力涡旋", "拖慢并拉退怪物", 155, 1.8f, 23),
        pulse("震荡脉冲", "环形冲击、短暂眩晕", 130, 1.9f, 26);
        public final String label, description;
        public final float range, reload, damage;
        TowerKind(String label, String description, float range, float reload, float damage){
            this.label = label; this.description = description; this.range = range; this.reload = reload; this.damage = damage;
        }
    }
    public enum EnemyKind{
        grunt("突击兽", 1f, 1f), runner("疾跑兽", 0.65f, 1.55f), swarm("蜂群", 0.45f, 1.2f),
        armor("铁甲巨兽", 2.3f, 0.72f), shield("护盾卫士", 1.5f, 0.95f), regen("再生怪", 1.3f, 0.95f),
        medic("治疗祭司", 1.1f, 0.9f), splitter("分裂母体", 1.4f, 0.9f), bomber("自爆虫", 1f, 1.12f),
        siege("远程攻城兽", 1.4f, 0.8f), jammer("电磁干扰者", 1.15f, 0.95f),
        burrow("钻地虫", 1.1f, 1.02f), flyer("空袭翼龙", 0.9f, 1.25f), leech("吸血猎手", 1.2f, 1.05f),
        boss("混沌首领", 9f, 0.62f);
        public final String label;
        public final float health, speed;
        EnemyKind(String label, float health, float speed){ this.label = label; this.health = health; this.speed = speed; }
    }
    public static class Tower{
        public final int pad;
        public final TowerKind kind;
        public int level = 1;
        public float health = 260f, cooldown, jam, angle;
        Tower(int pad, TowerKind kind){ this.pad = pad; this.kind = kind; health = maxHealth(); }
        public float maxHealth(){ return 260f + level * 30f; }
    }
    public static class Enemy{
        public final EnemyKind kind;
        public final int route;
        public final Vec2 position = new Vec2();
        public final float maxHealth;
        public float health, shield, progress, slow, burn, poison, stun, attack, ability, angle, hitFlash;
        public boolean child;
        Enemy(EnemyKind kind, int route, float health){
            this.kind = kind; this.route = route; this.health = maxHealth = health;
            shield = kind == EnemyKind.shield ? health * 0.65f : 0f;
        }
        public boolean alive(){ return health > 0f; }
    }
    /** A hit resolves when the visible projectile arrives. Beams have a short visible lifetime. */
    public static class Shot{
        public final Vec2 from, to;
        public final Enemy target;
        public final TowerKind kind;
        public final float damage, duration;
        public float age;
        public boolean resolved, returning;
        public Enemy attacker;
        public final int enemyPad;
        Shot(Vec2 from, Vec2 to, Enemy target, TowerKind kind, float damage, float duration, int enemyPad){
            this.from = from.cpy(); this.to = to.cpy(); this.target = target; this.kind = kind;
            this.damage = damage; this.duration = duration; this.enemyPad = enemyPad;
        }
        public boolean hostile(){ return enemyPad >= -1; }
    }

    public final PinyinDefenseMap map;
    public final Seq<Enemy> enemies = new Seq<>();
    public final Seq<Shot> shots = new Seq<>();
    public final Tower[] towers = new Tower[PinyinDefenseMap.padCount];
    public final Seq<TowerKind> deck = new Seq<>();
    public final float[] constructionCooldown = new float[2];
    public Feedback feedback;
    public int wave, resources = 2, correct, wrong, kills, leaked, spawned;
    public float baseHealth = baseMax, elapsed, nextWave = waveInterval, resourceTimer, transition;
    public float towerDamage, playerDamage, baseDamage;
    public boolean won, lost;
    private final Rand random;
    private final int chapter;
    private float accumulator, spawnTimer, pressure;
    private int pending, spawnIndex, pendingAnswers;
    private final float[] playerCooldown = new float[2];
    private final Seq<Enemy> children = new Seq<>();

    public PinyinDefense(long seed, int chapter){
        this.chapter = Math.max(1, chapter);
        map = new PinyinDefenseMap(seed, chapter);
        random = new Rand(seed ^ 0x5946abL);
        // A rotating catalog guarantees six distinct choices and a changed catalog each map.
        int offset = Math.floorMod((int)(seed ^ (seed >>> 32)) + chapter - 1, TowerKind.values().length);
        for(int i = 0; i < 6; i++) deck.add(TowerKind.values()[(offset + i * 3) % TowerKind.values().length]);
        towers[0] = new Tower(0, deck.get(0));
        towers[1] = new Tower(1, deck.get(1));
    }

    public int chapter(){ return chapter; }
    public float research(){ return 1f + correct * 0.065f; }
    public TowerKind recommendedKind(int pad){ return deck.get(pad % deck.size); }
    public int mistakeTolerance(){ return Math.max(1, 4 - (chapter - 1) / 2 - wave / 7); }
    public boolean cleared(){ return wave == waves && pending == 0 && enemies.isEmpty(); }
    public int towerCount(){ int count = 0; for(Tower tower : towers) if(tower != null) count++; return count; }
    public int recommendedPad(){
        // Build more firing lanes early, then upgrade the least developed socket.
        if(towerCount() < Math.min(towers.length, 2 + correct / 3)){
            for(int i = 0; i < towers.length; i++) if(towers[i] == null) return i;
        }
        int best = 0;
        for(int i = 0; i < towers.length; i++) if(towers[i] != null && (towers[best] == null || towers[i].level < towers[best].level)) best = i;
        return best;
    }

    /** Called only after a question reserves a refreshed crystal. A wrong choice spends it too. */
    public boolean reserveResource(){
        if(resources <= 0 || won || lost) return false;
        resources--;
        pendingAnswers++;
        return true;
    }

    public boolean reserveConstruction(int slot){
        if(slot < 0 || slot >= constructionCooldown.length || constructionCooldown[slot] > 0f || !reserveResource()) return false;
        constructionCooldown[slot] = constructionInterval;
        return true;
    }

    public void answer(boolean right, int pad, TowerKind kind){
        if(won || lost || pendingAnswers <= 0 || pad < 0 || pad >= towers.length || kind == null || !deck.contains(kind, true)) return;
        pendingAnswers--;
        if(right){
            correct++;
            Tower tower = towers[pad];
            if(tower == null) towers[pad] = tower = new Tower(pad, kind);
            else tower.level++;
            tower.health = tower.maxHealth();
            tower.jam = 0f;
            pressure = Math.max(0f, pressure - 0.65f);
        }else{
            wrong++;
            pressure += 1f;
            // Reinforcements enter the last road section, then physically march to and attack the base.
            if(pressure >= mistakeTolerance()){
                for(int i = 0; i < 2; i++){
                    Enemy enemy = spawn(EnemyKind.runner, i % map.routes.size, 2f + wave * 0.12f);
                    if(enemy != null){
                        enemy.shield = enemy.health * 0.35f;
                        enemy.progress = map.lengths[enemy.route] - 125f - i * 35f;
                        map.position(enemy.route, enemy.progress, enemy.position);
                    }
                }
                pressure = Math.max(0f, pressure - 0.5f);
            }
        }
    }

    public void update(float seconds){
        if(won || lost) return;
        accumulator += Math.max(0f, Math.min(seconds, 1f));
        while(accumulator >= 1f / 30f){
            step(1f / 30f);
            accumulator -= 1f / 30f;
            if(won || lost) break;
        }
    }

    private void step(float dt){
        elapsed += dt;
        for(int slot = 0; slot < constructionCooldown.length; slot++) constructionCooldown[slot] = Math.max(0f, constructionCooldown[slot] - dt);
        resourceTimer += dt;
        if(resourceTimer >= resourceInterval){ resourceTimer -= resourceInterval; resources = Math.min(3, resources + 1); }
        nextWave -= dt;
        if(wave < waves && pending == 0 && nextWave <= 0f){
            wave++;
            pending = 6 + wave / 2;
            spawnIndex = 0;
            spawnTimer = 0f;
            nextWave = waveInterval;
        }
        if(pending > 0){
            spawnTimer -= dt;
            if(spawnTimer <= 0f && enemies.size < maxEnemies - 3){
                EnemyKind kind = wave % 5 == 0 && spawnIndex == 0 ? EnemyKind.boss : roster(wave, spawnIndex);
                spawn(kind, spawnIndex % map.routes.size);
                pending--; spawnIndex++; spawnTimer = 0.65f;
            }
        }
        for(Tower tower : towers){
            if(tower == null) continue;
            tower.jam = Math.max(0f, tower.jam - dt);
            tower.cooldown -= dt;
            if(tower.cooldown > 0f || tower.jam > 0f) continue;
            Vec2 origin = map.pads[tower.pad];
            Enemy target = target(origin, tower.kind.range, tower.kind == TowerKind.rail);
            if(target == null) continue;
            tower.angle = origin.angleTo(target.position);
            fire(tower, target);
            tower.cooldown = tower.kind.reload / (1f + Math.min(tower.level, 15) * 0.025f);
        }
        children.clear();
        for(Enemy enemy : enemies){
            if(!enemy.alive()) continue;
            enemy.hitFlash = Math.max(0f, enemy.hitFlash - dt);
            enemy.slow = Math.max(0f, enemy.slow - dt);
            enemy.stun = Math.max(0f, enemy.stun - dt);
            if(enemy.burn > 0f){ enemy.burn -= dt; hurt(enemy, 9f * research() * dt, true); }
            if(enemy.poison > 0f){ enemy.poison -= dt; hurt(enemy, 8f * research() * dt, true); }
            if(!enemy.alive()) continue;
            enemy.ability -= dt;
            if(enemy.ability <= 0f){ ability(enemy); enemy.ability = 3.5f; }
            float stop = map.lengths[enemy.route] - (enemy.kind == EnemyKind.siege ? 95f : 18f);
            if(enemy.progress < stop){
                float speed = 32f * enemy.kind.speed * (1f + wave * 0.005f) * (enemy.slow > 0f ? 0.5f : 1f);
                if(enemy.stun <= 0f) enemy.progress = Math.min(stop, enemy.progress + speed * dt);
                Vec2 old = enemy.position.cpy();
                map.position(enemy.route, enemy.progress, enemy.position);
                if(!old.epsilonEquals(enemy.position, 0.01f)) enemy.angle = old.angleTo(enemy.position);
            }
            enemy.attack -= dt;
            boolean atBase = enemy.progress >= stop;
            if(enemy.attack <= 0f){
                if(atBase){
                    leaked++;
                    hostileShot(enemy, -1, enemy.kind == EnemyKind.boss ? 32f : 8f + wave * 0.65f);
                    if(enemy.kind == EnemyKind.bomber){
                        for(Tower tower : towers) if(tower != null && map.pads[tower.pad].dst(enemy.position) < 130f) tower.health -= 85f;
                        enemy.health = 0f;
                    }
                    enemy.attack = enemy.kind == EnemyKind.siege ? 2.3f : 1.25f;
                }else if(enemy.kind == EnemyKind.siege || enemy.kind == EnemyKind.bomber || enemy.kind == EnemyKind.boss || enemy.kind == EnemyKind.flyer){
                    int pad = nearestTower(enemy.position, 120f);
                    if(pad >= 0) hostileShot(enemy, pad, enemy.kind == EnemyKind.boss ? 38f : 16f);
                    enemy.attack = 2.5f;
                }
            }
        }
        for(int i = shots.size - 1; i >= 0; i--){
            Shot shot = shots.get(i);
            shot.age += dt;
            if(shot.target != null && shot.target.alive() && !shot.returning) shot.to.set(shot.target.position);
            if(!shot.resolved && shot.age >= shot.duration){
                impact(shot); shot.resolved = true;
                if(feedback != null) feedback.impacted(shot);
            }
            if(shot.age >= shot.duration + 0.2f) shots.remove(i);
        }
        for(int i = enemies.size - 1; i >= 0; i--){
            Enemy enemy = enemies.get(i);
            if(enemy.alive()) continue;
            kills++;
            if(feedback != null) feedback.destroyed(enemy);
            if(enemy.kind == EnemyKind.splitter && !enemy.child && enemies.size + children.size < maxEnemies - 2){
                for(int j = 0; j < 2; j++){
                    Enemy child = new Enemy(EnemyKind.swarm, enemy.route, enemy.maxHealth * 0.25f);
                    child.child = true; child.progress = Math.max(0f, enemy.progress - 12f * j);
                    map.position(child.route, child.progress, child.position); children.add(child);
                }
            }
            enemies.remove(i);
        }
        enemies.addAll(children);
        for(int i = 0; i < towers.length; i++) if(towers[i] != null && towers[i].health <= 0f){
            if(feedback != null) feedback.destroyed(towers[i]);
            towers[i] = null;
        }
        baseHealth = Math.max(0f, baseHealth);
        lost = baseHealth <= 0f;
        if(!lost && cleared()) won = true;
    }

    public EnemyKind roster(int wave, int slot){
        int available = Math.min(EnemyKind.values().length - 1, 4 + wave + chapter);
        return EnemyKind.values()[Math.floorMod(slot * 5 + wave * 3 + (int)(map.seed & 15), available)];
    }

    public Enemy spawn(EnemyKind kind, int route){
        return spawn(kind, route, 1f);
    }

    private Enemy spawn(EnemyKind kind, int route, float healthScale){
        if(enemies.size >= maxEnemies) return null;
        float growth = 1f + Math.max(0, wave - 1) * 0.3f;
        float difficulty = 1f + Math.min(chapter - 1, 15) * 0.07f;
        Enemy enemy = new Enemy(kind, route, 140f * kind.health * growth * difficulty * healthScale);
        map.position(route, 0f, enemy.position);
        enemies.add(enemy); spawned++;
        return enemy;
    }

    private void ability(Enemy enemy){
        if((enemy.kind == EnemyKind.regen || enemy.kind == EnemyKind.leech) && enemy.poison <= 0f) enemy.health = Math.min(enemy.maxHealth, enemy.health + enemy.maxHealth * 0.05f);
        if(enemy.kind == EnemyKind.medic){
            for(Enemy friend : enemies) if(friend.alive() && friend.poison <= 0f && friend.position.dst(enemy.position) < 90f) friend.health = Math.min(friend.maxHealth, friend.health + friend.maxHealth * 0.06f);
        }
        if(enemy.kind == EnemyKind.jammer){
            int pad = nearestTower(enemy.position, 105f);
            if(pad >= 0) towers[pad].jam = 1.1f;
        }
        if(enemy.kind == EnemyKind.burrow && enemy.progress > 1f) enemy.progress = Math.min(map.lengths[enemy.route] - 20f, enemy.progress + 18f);
        if(enemy.kind == EnemyKind.boss){
            int pad = nearestTower(enemy.position, 145f);
            if(pad >= 0) hostileShot(enemy, pad, 25f);
        }
    }

    private int nearestTower(Vec2 point, float range){
        int best = -1;
        for(Tower tower : towers) if(tower != null){
            float distance = map.pads[tower.pad].dst(point);
            if(distance < range){ range = distance; best = tower.pad; }
        }
        return best;
    }

    private Enemy target(Vec2 point, float range, boolean heavy){
        Enemy best = null;
        float score = -Float.MAX_VALUE;
        for(Enemy enemy : enemies) if(enemy.alive() && point.dst(enemy.position) <= range){
            float value = heavy ? enemy.maxHealth : enemy.progress / map.lengths[enemy.route];
            if(value > score){ score = value; best = enemy; }
        }
        return best;
    }

    private void fire(Tower tower, Enemy target){
        float damage = tower.kind.damage * research() * (1f + (tower.level - 1) * 0.14f);
        Vec2 origin = map.pads[tower.pad];
        int count = tower.kind == TowerKind.scatter ? 3 : tower.kind == TowerKind.missile ? 2 : 1;
        int fired = 0;
        for(Enemy enemy : enemies){
            if(!enemy.alive() || origin.dst(enemy.position) > tower.kind.range) continue;
            if(count == 1 && enemy != target) continue;
            shoot(origin, enemy, tower.kind, damage, false);
            if(++fired >= count) break;
        }
        if(tower.kind == TowerKind.missile && fired == 1) shoot(origin, target, tower.kind, damage, false);
    }

    private void shoot(Vec2 origin, Enemy target, TowerKind kind, float damage, boolean player){
        float duration = kind == TowerKind.laser || kind == TowerKind.lightning || kind == TowerKind.rail ? 0.04f
            : kind == TowerKind.artillery ? 0.8f : Math.max(0.12f, origin.dst(target.position) / 350f);
        if(shots.size < 400){
            Shot shot = new Shot(origin, target.position, target, kind, damage, duration, player ? -3 : -2);
            shots.add(shot);
            if(feedback != null) feedback.fired(shot);
        }
    }

    public void playerAttack(int slot, float x, float y, float seconds){
        if(won || lost) return;
        playerCooldown[slot] -= seconds;
        if(playerCooldown[slot] > 0f) return;
        Vec2 point = new Vec2(x, y);
        Enemy target = target(point, 150f, false);
        if(target != null){ shoot(point, target, TowerKind.rapid, 2f, true); playerCooldown[slot] = 0.65f; }
    }

    private void hostileShot(Enemy enemy, int pad, float damage){
        if(shots.size < 400){
            Shot shot = new Shot(enemy.position, pad < 0 ? map.base : map.pads[pad], null, null, damage, 0.5f, pad);
            shot.attacker = enemy;
            shots.add(shot);
            if(feedback != null) feedback.fired(shot);
        }
    }

    private void impact(Shot shot){
        if(shot.hostile()){
            if(shot.enemyPad == -1){ baseHealth -= shot.damage; baseDamage += shot.damage; }
            else if(towers[shot.enemyPad] != null) towers[shot.enemyPad].health -= shot.damage;
            if(shot.attacker != null && shot.attacker.kind == EnemyKind.leech && shot.attacker.alive() && shot.attacker.poison <= 0f){
                shot.attacker.health = Math.min(shot.attacker.maxHealth, shot.attacker.health + shot.damage * 2f);
            }
            return;
        }
        if(shot.enemyPad == -3){
            if(shot.target != null && shot.target.alive()){
                float before = shot.target.health;
                shot.target.health -= Math.min(shot.damage, shot.target.health);
                shot.target.hitFlash = 0.12f;
                playerDamage += before - shot.target.health;
            }
            return;
        }
        TowerKind kind = shot.kind;
        Enemy main = shot.target;
        if(main != null && main.alive()) hurt(main, shot.damage, kind == TowerKind.rail || kind == TowerKind.laser);
        if(shot.returning) return;
        int chain = 0;
        for(Enemy enemy : enemies){
            if(!enemy.alive()) continue;
            float distance = enemy.position.dst(shot.to);
            boolean area = switch(kind){
                case artillery, fragment, poison -> distance < 58f;
                case frost, flame, vortex, pulse -> distance < 65f;
                case lightning -> distance < 100f && chain < 3;
                case laser -> Intersector.distanceSegmentPoint(shot.from.x, shot.from.y, shot.to.x, shot.to.y, enemy.position.x, enemy.position.y) < 17f;
                default -> false;
            };
            if(area && enemy != main){
                hurt(enemy, shot.damage * (kind == TowerKind.lightning ? 0.65f : 0.55f), kind == TowerKind.laser);
                if(kind == TowerKind.lightning && shots.size < 400){
                    Shot arc = new Shot(shot.to, enemy.position, null, TowerKind.lightning, 0f, 0.18f, -2);
                    arc.resolved = true; shots.add(arc); chain++;
                }
                if(kind == TowerKind.fragment && shots.size < 400){
                    Shot shard = new Shot(shot.to, enemy.position, null, TowerKind.fragment, 0f, 0.18f, -2);
                    shard.resolved = true; shots.add(shard);
                }
            }
            if(enemy == main || area){
                if(kind == TowerKind.frost || kind == TowerKind.vortex) enemy.slow = 2f;
                if(kind == TowerKind.flame) enemy.burn = 3f;
                if(kind == TowerKind.poison) enemy.poison = 4f;
                if(kind == TowerKind.pulse) enemy.stun = 0.45f;
                if(kind == TowerKind.vortex) enemy.progress = Math.max(0f, enemy.progress - 10f);
            }
        }
        if(kind == TowerKind.boomerang && main != null && main.alive() && shots.size < 400){
            Shot back = new Shot(shot.to, shot.from, main, TowerKind.boomerang, shot.damage * 0.7f, 0.35f, -2);
            back.returning = true;
            shots.add(back);
        }
    }

    private void hurt(Enemy enemy, float damage, boolean pierce){
        if(!enemy.alive()) return;
        if(enemy.kind == EnemyKind.armor && !pierce) damage *= 0.58f;
        if(enemy.shield > 0f && !pierce){
            float absorbed = Math.min(enemy.shield, damage);
            enemy.shield -= absorbed; damage -= absorbed;
            if(enemy.shield <= 0f && feedback != null) feedback.shieldBroken(enemy);
        }
        if(damage >= 1f) enemy.hitFlash = 0.12f;
        float actual = Math.min(enemy.health, damage);
        enemy.health -= actual;
        towerDamage += actual;
    }
}
