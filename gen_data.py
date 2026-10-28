import json, os
R='src/main/resources/'
def w(p,o):
    os.makedirs(os.path.dirname(R+p),exist_ok=True); json.dump(o,open(R+p,'w'),indent=2)
armor=['helmet','chestplate','leggings','boots']; tools=['sword','pickaxe','axe','shovel','hoe']
items=['infinium_ingot']+['infinium_'+x for x in armor+tools]
for n in items:
    w(f'assets/infinium/items/{n}.json',{"model":{"type":"minecraft:model","model":f"infinium:item/{n}"}})
    w(f'assets/infinium/models/item/{n}.json',{"parent":"minecraft:item/handheld" if n.split('_')[-1] in tools else "minecraft:item/generated","textures":{"layer0":f"infinium:item/{n}"}})
w('assets/infinium/equipment/infinium.json',{"layers":{
  "humanoid":[{"texture":"infinium:infinium"}],"humanoid_leggings":[{"texture":"infinium:infinium"}],
  "wings":[{"texture":"infinium:infinium","use_player_texture":False}]}})
w('assets/infinium/blockstates/infinium_farmland.json',{"variants":{f"moisture={i}":{"model":"infinium:block/infinium_farmland"} for i in range(8)}})
w('assets/infinium/models/block/infinium_farmland.json',{"parent":"minecraft:block/template_farmland","textures":{"dirt":"minecraft:block/dirt","top":"infinium:block/infinium_farmland_moist"}})
for n in ('spacetime_fragments','spacetime_crystal'):   # animated textures: gen_new_items.py
    w(f'assets/infinium/items/{n}.json',{"model":{"type":"minecraft:model","model":f"infinium:item/{n}"}})
    w(f'assets/infinium/models/item/{n}.json',{"parent":"minecraft:item/generated","textures":{"layer0":f"infinium:item/{n}"}})
# keep the old texture path working until gen_new_items rewrites spacetime_fragments.png
import shutil
_old = R+'assets/infinium/textures/item/strange_fragments.png'
_new = R+'assets/infinium/textures/item/spacetime_fragments.png'
if os.path.exists(_old) and not os.path.exists(_new):
    shutil.copy(_old, _new)
    if os.path.exists(_old+'.mcmeta'): shutil.copy(_old+'.mcmeta', _new+'.mcmeta')
w('data/infinium/recipe/spacetime_crystal.json',{"type":"minecraft:crafting_shaped","category":"misc",
  "key":{"F":"infinium:spacetime_fragments","A":"minecraft:amethyst_shard"},"pattern":["FFF","FAF","FFF"],"result":{"id":"infinium:spacetime_crystal","count":1}})
lang={"item.infinium.spacetime_fragments":"Spacetime Fragments","item.infinium.strange_fragments":"Spacetime Fragments","item.infinium.spacetime_crystal":"Spacetime Crystal","item.infinium.infinium_ingot":"Infinium Ingot", "block.infinium.infinium_farmland":"Infinium Farmland","effect.infinium.bleeding":"Bleeding"}
for x in armor+tools: lang[f"item.infinium.infinium_{x}"]="Infinium "+x.capitalize()
w('assets/infinium/lang/en_us.json',lang)
# recipes
# Infinium ingot: Spacetime Crystal center, netherite ingots on the edges, corners = eye of ender, diamond block,
# wither skeleton skull, redstone block in ANY arrangement -> all 24 permutations as vanilla shaped recipes (JEI shows them)
import itertools, glob as _g
for f in _g.glob(R+'data/infinium/recipe/infinium_ingot*.json'): os.remove(f)
CORNERS={'E':'minecraft:ender_eye','D':'minecraft:diamond_block','W':'minecraft:wither_skeleton_skull','R':'minecraft:redstone_block'}
for i,(a,b,c,d) in enumerate(itertools.permutations('EDWR')):
    w(f'data/infinium/recipe/infinium_ingot{"" if i==0 else "_"+str(i)}.json',{"type":"minecraft:crafting_shaped","category":"misc","group":"infinium_ingot",
      "key":{"N":"minecraft:netherite_ingot","O":"minecraft:obsidian","A":"infinium:spacetime_crystal",**CORNERS},   # Spacetime Crystal center (user); R5 obsidian top/bottom
      "pattern":[a+"O"+b,"NAN",c+"O"+d],"result":{"id":"infinium:infinium_ingot","count":1}})
for x in armor+tools:
    if os.path.exists(R+f'data/infinium/recipe/infinium_{x}_smithing.json'): os.remove(R+f'data/infinium/recipe/infinium_{x}_smithing.json')   # old template-less upgrade
# netherite -> Infinium needs a style template; the template decides infinium:style (dye recolour recipes: gen_dye.py)
STYLES=[('astral','Astral',False),('cybernetic','Cybernetic',False),('singularity','Singularity',True),('cyberpsycho','CyberPsycho',True)]
import glob as _g2
for f in _g2.glob(R+'data/infinium/recipe/infinium_*_smithing.json'): os.remove(f)
for x in tools:   # tools: no template (empty slot = default glow; dye-in-template-slot variants: gen_dye.py). Style follows the holder's chestplate.
    w(f'data/infinium/recipe/infinium_{x}_smithing.json',{"type":"minecraft:smithing_transform",
      "base":f"minecraft:netherite_{x}","addition":"infinium:infinium_ingot","result":{"id":f"infinium:infinium_{x}"}})
for x in armor:   # Infinium armor: same-tier swap (template only, template IS consumed) or tier-2 upgrade
    for sid in ('astral','cybernetic','singularity','cyberpsycho'):  # swaps: Astral<->Cybernetic, Singularity<->Cyber Psycho (no ingot; StyleTemplates.allowed checks the base tier). (Cybernetic->CyberPsycho, Astral->Singularity: template + ingot). Base style is checked in SmithingTransformRecipeMixin.
        w(f'data/infinium/recipe/infinium_{x}_{sid}_restyle_smithing.json',{"type":"minecraft:smithing_transform",
          "template":f"infinium:{sid}_upgrade_smithing_template","base":f"infinium:infinium_{x}",
          "result":{"id":f"infinium:infinium_{x}","components":{"infinium:style":sid}}})
    for sid in ('singularity','cyberpsycho'):
        w(f'data/infinium/recipe/infinium_{x}_{sid}_upgrade_smithing.json',{"type":"minecraft:smithing_transform",
          "template":f"infinium:{sid}_upgrade_smithing_template","base":f"infinium:infinium_{x}","addition":"infinium:infinium_ingot",
          "result":{"id":f"infinium:infinium_{x}","components":{"infinium:style":sid}}})
for x in armor:   # armor: netherite -> Astral or Cybernetic only (tier 1); template + ingot consumed
    for sid,_,_ in STYLES[:2]:
        w(f'data/infinium/recipe/infinium_{x}_{sid}_smithing.json',{"type":"minecraft:smithing_transform",
          "template":f"infinium:{sid}_upgrade_smithing_template","base":f"minecraft:netherite_{x}","addition":"infinium:infinium_ingot",
          "result":{"id":f"infinium:infinium_{x}","components":{"infinium:style":sid}}})
for sid,name,_ in STYLES:
    n=f'{sid}_upgrade_smithing_template'
    w(f'assets/infinium/items/{n}.json',{"model":{"type":"minecraft:model","model":f"infinium:item/{n}"}})
    w(f'assets/infinium/models/item/{n}.json',{"parent":"minecraft:item/generated","textures":{"layer0":f"infinium:item/{n}"}})
    lang[f"item.infinium.{n}"]=f"{name} Upgrade"; lang[f"infinium.style.{sid}"]=name
lang.update({"infinium.style.line":"Style: %s","item.infinium.smithing_template.applies_to":"Netherite or Infinium Armor",
  "item.infinium.smithing_template.ingredients":"Infinium Ingot",
  "item.infinium.smithing_template.base_slot_description":"Add netherite or Infinium armor",
  "item.infinium.smithing_template.additions_slot_description":"Add Infinium Ingot (not used when swapping to the other style of the same tier)"})
TPL={'astral':("Netherite armor (+ Infinium Ingot); Cybernetic armor (swap, template consumed)","Add netherite armor or Cybernetic armor","Add Infinium Ingot (not used for a Cybernetic swap, the template is consumed)"),
     'cybernetic':("Netherite armor (+ Infinium Ingot); Astral armor (swap, template consumed)","Add netherite armor or Astral armor","Add Infinium Ingot (not used for an Astral swap, the template is consumed)"),
     'singularity':("Astral armor (+ Infinium Ingot); Cyber Psycho armor (swap, template consumed)","Add Astral or Cyber Psycho Infinium armor","Add Infinium Ingot (not used for a Cyber Psycho swap, the template is consumed)"),
     'cyberpsycho':("Cybernetic armor (+ Infinium Ingot); Singularity armor (swap, template consumed)","Add Cybernetic or Singularity Infinium armor","Add Infinium Ingot (not used for a Singularity swap, the template is consumed)")}
for sid,(a,b,c) in TPL.items():
    lang[f"item.infinium.{sid}_upgrade_smithing_template.applies_to"]=a
    lang[f"item.infinium.{sid}_upgrade_smithing_template.base_slot_description"]=b
    lang[f"item.infinium.{sid}_upgrade_smithing_template.additions_slot_description"]=c
w('assets/infinium/lang/en_us.json',lang)
# tags
w('data/infinium/tags/item/infinium_tool_materials.json',{"values":["infinium:infinium_ingot","minecraft:netherite_ingot"]})
tagmap={'head_armor':['helmet'],'chest_armor':['chestplate'],'leg_armor':['leggings'],'foot_armor':['boots'],
 'trimmable_armor':armor,'swords':['sword'],'pickaxes':['pickaxe'],'axes':['axe'],'shovels':['shovel'],'hoes':['hoe']}
for t,v in tagmap.items(): w(f'data/minecraft/tags/item/{t}.json',{"replace":False,"values":['infinium:infinium_'+x for x in v]})
for t in ['grows_crops','supports_crops','supports_vegetation','supports_big_dripleaf','support_override_cactus_flower','mineable/shovel']:
    w(f'data/minecraft/tags/block/{t}.json',{"replace":False,"values":["infinium:infinium_farmland"]})
w('data/infinium/loot_table/blocks/infinium_farmland.json',{"type":"minecraft:block","pools":[{"rolls":1.0,
  "conditions":[{"condition":"minecraft:survives_explosion"}],"entries":[{"type":"minecraft:item","name":"minecraft:dirt"}]}],
  "random_sequence":"infinium:blocks/infinium_farmland"})
exec(open('gen_bread.py').read())
exec(open('gen_r8.py').read())
w('fabric.mod.json',{"schemaVersion":1,"id":"infinium","version":"1.0.0","name":"Infinium",
 "description":"Infinium: a dark purple tier above netherite.","authors":["Justin Teller"],"license":"MIT","environment":"*",
 "entrypoints":{"main":["dev.justin.infinium.Infinium"],"client":["dev.justin.infinium.client.InfiniumClient"],"jei_mod_plugin":["dev.justin.infinium.compat.InfiniumJeiPlugin"],
   "modmenu":["dev.justin.infinium.client.compat.InfiniumModMenu"],"lambdynlights:initializer":["dev.justin.infinium.client.compat.InfiniumDynamicLights"]},
 "mixins":["infinium.mixins.json","infinium.compat.mixins.json",{"config":"infinium.client.mixins.json","environment":"client"}],
 "depends":{"fabricloader":">=0.19.3","minecraft":"26.2","fabric-api":"*","java":">=25","punchy":"*","roguelike":"*"},"suggests":{"jei":"*","modmenu":"*","lambdynlights":">=4.12.4"}})
w('infinium.mixins.json',{"required":True,"package":"dev.justin.infinium.mixin","compatibilityLevel":"JAVA_21",
 "mixins":["LivingEntityMixin","CropBlockMixin","BlockDropsMixin","AnvilMenuMixin","SmithingMenuMixin","SmithingTransformRecipeMixin","FireworkRocketEntityMixin","InventoryStackMixin","SlotStackMixin","ItemStackCodecMixin","BucketStackMixin","SolidBucketStackMixin","ItemStackSplitMixin","ItemStackDurabilityMixin","BowDrawMixin","CrossbowChargeMixin","BlockItemNoConsumeMixin","ConsumableNoConsumeMixin","FireworkNoConsumeMixin","ItemStackTemplateCodecMixin","EnchantDisableMixin","InfiniumInitMixin","ItemNameMixin","ItemEntityMixin"],"injectors":{"defaultRequire":1}})
w('infinium.compat.mixins.json',{"required":False,"package":"dev.justin.infinium.compat.mixin","plugin":"dev.justin.infinium.compat.CompatMixinPlugin","compatibilityLevel":"JAVA_21",
 "mixins":["tb.AttachmentUtilsMixin","tb.StorageOwnerAccessor","tb.ItemStackHandlerMixin"],"client":["jei.InventoryEffectRendererMixin","spatial.GuiRendererOverscanMixin","spatial.TextureTargetManagerMixin","spatial.AnimationUtilMixin","spatial.InventoryRendererMixin","spatial.RenderUtilMixin","spatial.ShouldHookScreenMixin","punchy.PunchyAttackStateMachineMixin"],"injectors":{"defaultRequire":0}})
w('infinium.client.mixins.json',{"required":True,"package":"dev.justin.infinium.client.mixin","compatibilityLevel":"JAVA_21",
 "client":["HudOverlayMixin","WingsLayerMixin","EquipmentLayerRendererMixin","ElytraModelMixin","HumanoidRenderStateMixin","MinecraftUseMixin","ClientTextTooltipMixin","TooltipNameMixin","SelectItemModelPropertiesAccessor","ContainerScreenMixin","ContainerScreenPosAccessor","SoundManagerMuteMixin","AnvilScreenMixin","SlotClickSoundMixin","CreativeSlotClickSoundMixin","AdvancementToastMixin","EffectsInInventoryMixin","ArmorRemoveGuardMixin","InfiniumClientBookMixin","TitleScreenFxMixin","ContainerLabelColorMixin","MinecraftAttackMixin","LightmapRenderStateExtractorMixin","SwordAnimItemInHandMixin","SwordAnimSwingMixin","SwordAnimRenderStateMixin","SwordAnimAvatarRendererMixin","SwordAnimHumanoidModelMixin"],"injectors":{"defaultRequire":1}})
print('ok')

exec(compile(open("gen_book.py", encoding="utf-8").read(), "gen_book.py", "exec"))
