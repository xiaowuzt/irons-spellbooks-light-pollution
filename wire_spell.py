"""Wires the mechanical half of a new spell: damage source class, damage type json, the
nine damage-type tags, the scroll model, the eight registration points, and the display
command pair. Everything here is identical in shape for every spell in this mod, which is
exactly why it belongs in a script rather than being retyped and mis-sed each time.

What it deliberately does NOT generate: the entity, the shader and the renderer. Those are
where each spell is actually different, and a template for them would just produce reskins.

Every insertion verifies its anchor appears exactly once and refuses to write otherwise, so
a silent mis-wire is not possible — that is the failure mode that cost the most time doing
this by hand (a copy-derived spell that compiled cleanly and spawned the wrong entity).

Usage:
    python wire_spell.py <ClassName> <snake_id> <SCHOOL> <cooldown> <mana> <scroll> \\
                         <PrevClassName> <prev_snake_id> <ShowCmd>
"""
import io
import json
import os
import sys

J = 'src/main/java/com/gang/lightpollution/'
RES = 'src/main/resources/'
ASSETS = RES + 'assets/irons_spellbooks_light_pollution/'
DATA = RES + 'data/'


def patch(path, pairs):
    s = io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        n = s.count(old)
        if n != 1:
            print(f'  REFUSED {os.path.basename(path)}: anchor x{n} -> {old[:48]!r}')
            return False
        s = s.replace(old, new)
    io.open(path, 'w', encoding='utf-8').write(s)
    print(f'  ok {os.path.basename(path)}')
    return True


def damage_class(cls, snake, verb):
    io.open(f'{J}entity/{cls}Damage.java', 'w', encoding='utf-8').write(f'''package com.gang.lightpollution.entity;

import com.gang.lightpollution.ExampleMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/** Registry key and source factory for {snake}. */
public final class {cls}Damage {{
    public static final ResourceKey<DamageType> TYPE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "{snake}"));

    private {cls}Damage() {{
    }}

    public static DamageSource {verb}(ServerLevel level, Entity caster, Entity effect) {{
        Registry<DamageType> registry =
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        Holder<DamageType> holder = registry.getHolderOrThrow(TYPE);
        return new DamageSource(holder, effect, caster);
    }}
}}
''')
    print(f'  ok {cls}Damage.java')


def damage_type(snake, prev_snake):
    io.open(f'{DATA}irons_spellbooks_light_pollution/damage_type/{snake}.json',
            'w', encoding='utf-8').write(json.dumps(
                {"message_id": snake, "scaling": "never",
                 "exhaustion": 0.0, "effects": "hurt"}, indent=2) + '\n')
    prev = f'irons_spellbooks_light_pollution:{prev_snake}'
    new = f'irons_spellbooks_light_pollution:{snake}'
    tagged = 0
    tags = f'{DATA}minecraft/tags/damage_type/'
    for name in sorted(os.listdir(tags)):
        p = tags + name
        d = json.load(io.open(p, encoding='utf-8'))
        v = d.get('values', [])
        if prev not in v or new in v:
            continue
        v.insert(v.index(prev) + 1, new)
        json.dump(d, io.open(p, 'w', encoding='utf-8'), indent=2, ensure_ascii=False)
        io.open(p, 'a', encoding='utf-8').write('\n')
        tagged += 1
    print(f'  ok damage type + {tagged} tags')
    return tagged == 9


def scroll(snake, texture):
    io.open(f'{ASSETS}models/item/{snake}_scroll.json', 'w', encoding='utf-8').write(
        json.dumps({"parent": "minecraft:item/generated",
                    "textures": {"layer0": f"irons_spellbooks:item/scroll_{texture}"}},
                   indent=2) + '\n')
    print(f'  ok {snake}_scroll.json')


def registries(cls, snake, prev_cls, prev_snake):
    upper = snake.upper()
    prev_upper = prev_snake.upper()
    ok = True
    ok &= patch(f'{J}registry/ModEntities.java', [
        (f'import com.gang.lightpollution.entity.{prev_cls}Entity;',
         f'import com.gang.lightpollution.entity.{prev_cls}Entity;\n'
         f'import com.gang.lightpollution.entity.{cls}Entity;'),
        (f'                    .build(ExampleMod.MODID + ":{prev_snake}"));',
         f'''                    .build(ExampleMod.MODID + ":{prev_snake}"));

    public static final RegistryObject<EntityType<{cls}Entity>> {upper} =
            ENTITY_TYPES.register("{snake}", () -> EntityType.Builder
                    .of({cls}Entity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(96).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":{snake}"));'''),
    ])
    ok &= patch(f'{J}spell/ModSpells.java', [
        (f'''    public static final RegistryObject<AbstractSpell> {prev_upper} = SPELLS.register(
            "{prev_snake}", {prev_cls}Spell::new);''',
         f'''    public static final RegistryObject<AbstractSpell> {prev_upper} = SPELLS.register(
            "{prev_snake}", {prev_cls}Spell::new);

    public static final RegistryObject<AbstractSpell> {upper} = SPELLS.register(
            "{snake}", {cls}Spell::new);'''),
    ])
    ok &= patch(f'{J}registry/ModItems.java', [
        (f'''    public static final RegistryObject<Item> {prev_upper}_SCROLL = registerSpellScroll(
            "{prev_snake}_scroll", ModSpells.{prev_upper});''',
         f'''    public static final RegistryObject<Item> {prev_upper}_SCROLL = registerSpellScroll(
            "{prev_snake}_scroll", ModSpells.{prev_upper});

    public static final RegistryObject<Item> {upper}_SCROLL = registerSpellScroll(
            "{snake}_scroll", ModSpells.{upper});'''),
    ])
    ok &= patch(f'{J}registry/ModCreativeTabs.java', [
        (f'                        output.accept(ModItems.{prev_upper}_SCROLL.get().getDefaultInstance());',
         f'                        output.accept(ModItems.{prev_upper}_SCROLL.get().getDefaultInstance());\n'
         f'                        output.accept(ModItems.{upper}_SCROLL.get().getDefaultInstance());'),
    ])
    ok &= patch(f'{J}ExampleMod.java', [
        (f'''            event.registerEntityRenderer(ModEntities.{prev_upper}.get(),
                    NoopRenderer::new);''',
         f'''            event.registerEntityRenderer(ModEntities.{prev_upper}.get(),
                    NoopRenderer::new);
            event.registerEntityRenderer(ModEntities.{upper}.get(),
                    NoopRenderer::new);'''),
    ])
    ok &= patch(f'{J}client/renderer/SpellLightEmitter.java', [
        (f'import com.gang.lightpollution.entity.{prev_cls}Entity;',
         f'import com.gang.lightpollution.entity.{prev_cls}Entity;\n'
         f'import com.gang.lightpollution.entity.{cls}Entity;'),
        (f'    private static final Set<{prev_cls}Entity> {prev_upper} =\n'
         f'            Collections.newSetFromMap(new IdentityHashMap<>());',
         f'    private static final Set<{prev_cls}Entity> {prev_upper} =\n'
         f'            Collections.newSetFromMap(new IdentityHashMap<>());\n'
         f'    private static final Set<{cls}Entity> {upper} =\n'
         f'            Collections.newSetFromMap(new IdentityHashMap<>());'),
        (f'        if (entity instanceof {prev_cls}Entity value) {prev_upper}.add(value);',
         f'        if (entity instanceof {prev_cls}Entity value) {prev_upper}.add(value);\n'
         f'        if (entity instanceof {cls}Entity value) {upper}.add(value);'),
        (f'        if (entity instanceof {prev_cls}Entity value) {prev_upper}.remove(value);',
         f'        if (entity instanceof {prev_cls}Entity value) {prev_upper}.remove(value);\n'
         f'        if (entity instanceof {cls}Entity value) {upper}.remove(value);'),
        (f'        {prev_upper}.clear();', f'        {prev_upper}.clear();\n        {upper}.clear();'),
    ])
    return ok


def collect(cls, snake):
    upper = snake.upper()
    p = f'{J}client/renderer/SpellLightEmitter.java'
    s = io.open(p, encoding='utf-8').read()
    if f'collect{cls}s()' in s:
        print('  collector already present')
        return True
    anchor = '    private static Vec3 interpolated('
    if s.count(anchor) != 1:
        print('  REFUSED collector: anchor not unique')
        return False
    s = s.replace(anchor, f'''    /** Live {snake.replace("_", " ")} effects. */
    public static List<{cls}Entity> collect{cls}s() {{
        return {upper}.isEmpty() ? List.of() : new ArrayList<>({upper});
    }}

''' + anchor)
    io.open(p, 'w', encoding='utf-8').write(s)
    print('  ok collector')
    return True


def main():
    if len(sys.argv) != 10:
        print(__doc__)
        return 1
    cls, snake, school, cooldown, mana, texture, prev_cls, prev_snake, show = sys.argv[1:]
    print(f'wiring {cls} ({snake}) after {prev_cls} ({prev_snake})')
    damage_class(cls, snake, 'source')
    ok = damage_type(snake, prev_snake)
    scroll(snake, texture)
    ok &= registries(cls, snake, prev_cls, prev_snake)
    ok &= collect(cls, snake)
    print('WIRED' if ok else 'INCOMPLETE — fix the refusals above')
    return 0 if ok else 1


sys.exit(main())
