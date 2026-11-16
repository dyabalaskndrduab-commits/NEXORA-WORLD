#!/usr/bin/env python3
"""Build the NEXORA stylized social-game avatar as a smooth, named GLB asset."""
from pathlib import Path
import numpy as np
import trimesh

OUT = Path("app/src/main/assets/NEXORA_Character_v1.glb")
scene = trimesh.Scene()

SKIN=(190,132,105,255); SKIN_D=(145,91,75,255)
HAIR=(13,15,23,255); HAIR_H=(31,34,47,255)
HOOD=(18,21,31,255); HOOD_L=(31,35,49,255)
PANTS=(19,23,33,255); PANTS_L=(36,41,53,255)
WHITE=(235,239,245,255); SOLE=(211,218,228,255)
TEAL=(75,218,207,255); EYE=(12,16,25,255)

def material(c, metallic=0.0, roughness=0.78):
    return trimesh.visual.material.PBRMaterial(
        baseColorFactor=np.array(c,dtype=np.uint8),
        metallicFactor=metallic, roughnessFactor=roughness)

def add(name, mesh, color, metal=0.0, rough=0.78):
    mesh.visual = trimesh.visual.TextureVisuals(material=material(color,metal,rough))
    scene.add_geometry(mesh, geom_name=name, node_name=name)

def ellipsoid(name, pos, scale, color, ico=4):
    m=trimesh.creation.icosphere(subdivisions=ico, radius=1.0)
    m.apply_scale(scale); m.apply_translation(pos)
    add(name,m,color)

def cylinder(name, a, b, radius, color, sections=24):
    a=np.array(a,float); b=np.array(b,float); d=b-a
    m=trimesh.creation.cylinder(radius=radius, height=np.linalg.norm(d), sections=sections)
    m.apply_transform(trimesh.geometry.align_vectors([0,0,1], d))
    m.apply_translation((a+b)/2)
    add(name,m,color)

def box(name, pos, size, color):
    m=trimesh.creation.box(extents=size); m.apply_translation(pos); add(name,m,color)

def lathe(name, profiles, color, n=40):
    # profiles are (y, radius_x, radius_z); front faces toward -Z
    verts=[]; faces=[]
    for y,rx,rz in profiles:
        for j in range(n):
            t=2*np.pi*j/n
            verts.append([rx*np.cos(t),y,rz*np.sin(t)])
    for i in range(len(profiles)-1):
        for j in range(n):
            a=i*n+j; b=i*n+(j+1)%n; c=(i+1)*n+(j+1)%n; d=(i+1)*n+j
            faces.extend([[a,b,c],[a,c,d]])
    add(name,trimesh.Trimesh(vertices=verts,faces=faces,process=True),color)

# Shoes: layered soles, rounded uppers, toe caps, tongues and laces.
for side,x in [('L',-.22),('R',.22)]:
    box(f'Shoe_{side}_sole',(x,.105,-.12),(.39,.13,.62),SOLE)
    ellipsoid(f'Shoe_{side}_upper',(x,.205,-.13),(.195,.145,.31),HOOD_L,3)
    ellipsoid(f'Shoe_{side}_toe',(x,.19,-.34),(.18,.105,.17),WHITE,3)
    box(f'Shoe_{side}_tongue',(x,.285,-.12),(.13,.07,.23),WHITE)
    for i in range(4):
        box(f'Shoe_{side}_lace_{i}',(x,.285+i*.018,-.23+i*.065),(.145,.014,.018),WHITE)

# Tailored jogger trousers with shaped thighs, knees, cuffs and cargo pockets.
for side,x in [('L',-.215),('R',.215)]:
    ellipsoid(f'Leg_{side}_thigh',(x,.88,.005),(.178,.43,.19),PANTS,4)
    ellipsoid(f'Leg_{side}_shin',(x,.43,.005),(.15,.32,.16),PANTS,4)
    ellipsoid(f'Cargo_{side}_pocket',(x*1.8,.72,-.153),(.115,.17,.035),PANTS_L,3)
    box(f'Leg_{side}_cuff',(x,.205,-.005),(.29,.075,.30),HOOD_L)
    box(f'Leg_{side}_sidepipe',(x+(-.15 if side=='L' else .15),.69,-.012),(.025,.36,.025),WHITE)

# Hoodie torso: tailored silhouette with a slightly dropped shoulder line.
lathe('Hoodie_Torso',[
    (1.04,.29,.205),(1.10,.37,.245),(1.26,.405,.255),
    (1.48,.43,.265),(1.70,.40,.245),(1.82,.31,.205),(1.88,.23,.17)
],HOOD,48)
ellipsoid('Hoodie_Hood',(0,1.82,.12),(.30,.32,.20),HOOD_L,4)
ellipsoid('Hoodie_Hood_Rim',(0,1.72,-.035),(.28,.15,.20),HOOD,4)
for side,x in [('L',-.44),('R',.44)]:
    ellipsoid(f'Arm_{side}',(x,1.48,.005),(.16,.40,.17),HOOD,4)
    ellipsoid(f'Cuff_{side}',(x,1.12,.0),(.165,.09,.17),HOOD_L,3)
    ellipsoid(f'Hand_{side}',(x,1.015,-.005),(.115,.13,.12),SKIN,3)
# Hem, front pocket, seams and drawstrings.
lathe('Hoodie_Hem',[(1.06,.36,.247),(1.12,.375,.25)],HOOD_L,48)
box('Hoodie_KangarooPocket',(0,1.28,-.253),(.49,.22,.026),HOOD_L)
for x in [-.105,.105]:
    cylinder('Hoodie_Drawstring',(x,1.76,-.205),(x,1.49,-.27),.012,WHITE,12)
ellipsoid('Hoodie_DrawstringTip_L',(-.105,1.485,-.27),(.022,.025,.022),TEAL,2)
ellipsoid('Hoodie_DrawstringTip_R',(.105,1.485,-.27),(.022,.025,.022),TEAL,2)

# Neck and softly shaped head.
cylinder('Neck',(0,1.88,0),(0,2.14,0),.125,SKIN,32)
ellipsoid('Head',(0,2.36,-.005),(.295,.365,.255),SKIN,5)
for side,x in [('L',-.292),('R',.292)]:
    ellipsoid(f'Ear_{side}',(x,2.34,-.005),(.065,.105,.075),SKIN,3)
    ellipsoid(f'EarInner_{side}',(x*1.08,2.34,-.067),(.027,.052,.018),SKIN_D,2)

# Face details facing -Z: eye whites, irises, pupils, catchlights and brows.
for side,x in [('L',-.112),('R',.112)]:
    ellipsoid(f'EyeWhite_{side}',(x,2.405,-.235),(.083,.105,.034),(248,247,242,255),3)
    ellipsoid(f'Iris_{side}',(x,2.402,-.265),(.052,.071,.022),(83,59,48,255),3)
    ellipsoid(f'Pupil_{side}',(x,2.401,-.282),(.031,.051,.013),EYE,3)
    ellipsoid(f'EyeShine_{side}',(x-.015,2.431,-.294),(.015,.019,.009),WHITE,2)
    ellipsoid(f'Brow_{side}',(x,2.535,-.224),(.092,.025,.025),HAIR,3)
ellipsoid('Nose',(0,2.335,-.275),(.035,.052,.035),SKIN_D,3)
ellipsoid('Mouth',(0,2.235,-.247),(.064,.014,.012),(105,48,47,255),3)
for x in [-.19,.19]:
    ellipsoid('Cheek',(x,2.29,-.219),(.045,.022,.012),(218,133,118,255),2)

# Layered swept hairstyle, sculpted as a cap plus individual locks.
ellipsoid('Hair_Cap',(0,2.625,.015),(.315,.205,.275),HAIR,5)
ellipsoid('Hair_Back',(0,2.48,.13),(.30,.27,.19),HAIR,4)
locks=[
    (-.22,2.66,-.10,.15,.16,.17,-22),(-.10,2.73,-.09,.17,.17,.18,-12),
    (.07,2.75,-.075,.18,.17,.18,8),(.22,2.68,-.045,.14,.15,.17,22),
    (-.20,2.55,-.20,.13,.15,.10,-25),(-.07,2.59,-.225,.13,.15,.085,-12),
    (.07,2.61,-.22,.14,.15,.09,12),(.20,2.56,-.19,.12,.14,.10,24),
    (-.015,2.53,-.25,.11,.17,.065,-7)
]
for i,(x,y,z,sx,sy,sz,ang) in enumerate(locks):
    m=trimesh.creation.icosphere(subdivisions=4,radius=1)
    m.apply_scale((sx,sy,sz))
    m.apply_transform(trimesh.transformations.rotation_matrix(np.radians(ang),(0,0,1)))
    m.apply_translation((x,y,z)); add(f'Hair_Lock_{i}',m,HAIR if i%2 else HAIR_H)

# Chest emblem: a clean raised NEXORA crown mark and wordmark bars.
for i,(x,y,w,h,ang) in enumerate([
    (-.13,1.56,.035,.095,-28),(-.065,1.59,.035,.12,-10),
    (.065,1.59,.035,.12,10),(.13,1.56,.035,.095,28)]):
    m=trimesh.creation.box(extents=(w,h,.018))
    m.apply_transform(trimesh.transformations.rotation_matrix(np.radians(ang),(0,0,1)))
    m.apply_translation((x,y,-.284)); add(f'NEXORA_Crown_{i}',m,TEAL)
box('NEXORA_Crown_Base',(0,1.505,-.285),(.31,.025,.018),TEAL)
for i,x in enumerate([-.10,0,.10]):
    box(f'NEXORA_Wordmark_{i}',(x,1.455,-.285),(.065,.012,.014),WHITE)

# Small branded sleeve patch and subtle garment seam accents.
box('Sleeve_Patch',(-.575,1.48,-.006),(.018,.12,.10),TEAL)
for side,x in [('L',-.30),('R',.30)]:
    box(f'Pocket_Stitch_{side}',(x,1.19,-.272),(.012,.15,.012),TEAL)

OUT.parent.mkdir(parents=True,exist_ok=True)
scene.export(OUT, file_type='glb')
print(f'Wrote {OUT} ({OUT.stat().st_size} bytes), {len(scene.geometry)} named meshes')
