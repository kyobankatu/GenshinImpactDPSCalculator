package simulation.party;

import mechanics.data.TalentDataManager;
import model.character.Alhaitham;
import model.character.Arlecchino;
import model.character.Beidou;
import model.character.Bennett;
import model.character.Chevreuse;
import model.character.Chiori;
import model.character.Citlali;
import model.character.Collei;
import model.character.Columbina;
import model.character.Diluc;
import model.character.Emilie;
import model.character.Faruzan;
import model.character.Fischl;
import model.character.Furina;
import model.character.Gaming;
import model.character.Ganyu;
import model.character.HuTao;
import model.character.Ineffa;
import model.character.Keqing;
import model.character.KukiShinobu;
import model.character.KaedeharaKazuha;
import model.character.Kirara;
import model.character.Layla;
import model.character.LanYan;
import model.character.Mona;
import model.character.Nahida;
import model.character.Navia;
import model.character.Ningguang;
import model.character.Ororon;
import model.character.Rosaria;
import model.character.SangonomiyaKokomi;
import model.character.Shenhe;
import model.character.Sucrose;
import model.character.Tighnari;
import model.character.Xiao;
import model.character.Xianyun;
import model.character.Xingqiu;
import model.character.Xiangling;
import model.character.Wanderer;
import model.character.Xilonen;
import model.character.YaeMiko;
import model.character.Yelan;
import model.character.Yoimiya;
import model.character.Zhongli;
import model.entity.Character;
import model.weapon.CalamityQueller;
import model.weapon.Deathmatch;
import model.weapon.FavoniusCodex;
import model.weapon.FavoniusGreatsword;
import model.weapon.FavoniusLance;
import model.weapon.FavoniusSword;
import model.weapon.FavoniusWarbow;
import model.weapon.LumidouceElegy;
import model.weapon.NocturnesCurtainCall;
import model.weapon.PeakPatrolSong;
import model.weapon.Rust;
import model.weapon.SacrificialSword;
import model.weapon.SkywardBlade;
import model.weapon.StaffOfHoma;
import model.weapon.TheStringless;
import model.weapon.ThrillingTalesOfDragonSlayers;
import model.weapon.TulaytullahsRemembrance;
import model.weapon.UrakuMisugiri;
import model.weapon.Verdict;
import model.weapon.WanderingEvenstar;
import model.weapon.WolfFang;
import model.weapon.WolfsGravestone;

/** Deterministic C0/base-loadout factories for the curated rotation campaign. */
final class CuratedCharacters {
    private static final double FIXED_RANDOM_DRAW = 0.5;

    private CuratedCharacters() {
    }

    static Character alhaitham() {
        return new Alhaitham(new FavoniusSword(5, () -> FIXED_RANDOM_DRAW), null, 0);
    }

    static Character xiao() {
        return new Xiao(new FavoniusLance(5, () -> FIXED_RANDOM_DRAW), null, 0);
    }

    static Character wanderer() {
        return new Wanderer(new TulaytullahsRemembrance(1), null, 0);
    }

    static Character diluc() {
        return new Diluc(new WolfsGravestone(1), null, 0);
    }

    static Character huTao() {
        return new HuTao(
                new StaffOfHoma(1), null, TalentDataManager.getInstance(), 0,
                () -> FIXED_RANDOM_DRAW);
    }

    static Character furina() {
        return new Furina(new WolfFang(), null, 0);
    }

    static Character yelan() {
        return new Yelan(new FavoniusWarbow(5, () -> FIXED_RANDOM_DRAW), null, 0);
    }

    static Character arlecchino() {
        return new Arlecchino(new Deathmatch(1), null, 0);
    }

    static Character citlali() {
        return new Citlali(new FavoniusCodex(5, () -> FIXED_RANDOM_DRAW), null, 0);
    }

    static Character emilie() {
        return new Emilie(new LumidouceElegy(1), null, 0);
    }

    static Character lanYan() {
        return new LanYan(new ThrillingTalesOfDragonSlayers(5), null, 6);
    }

    static Character lanYanFavonius() {
        return new LanYan(new FavoniusCodex(5, () -> FIXED_RANDOM_DRAW), null, 6);
    }

    static Character mona() {
        return new Mona(new FavoniusCodex(5, () -> FIXED_RANDOM_DRAW), null, 0,
                () -> FIXED_RANDOM_DRAW);
    }

    static Character ineffa() {
        return new Ineffa(new CalamityQueller(), null);
    }

    static Character columbina() {
        return new Columbina(new NocturnesCurtainCall(), null,
                () -> FIXED_RANDOM_DRAW);
    }

    static Character xilonen() {
        return new Xilonen(new PeakPatrolSong(1), null, 0);
    }

    static Character tighnari() {
        return new Tighnari(new TheStringless(), null, TalentDataManager.getInstance(), 0,
                () -> FIXED_RANDOM_DRAW);
    }

    static Character ganyu() {
        return new Ganyu(new FavoniusWarbow(5, () -> FIXED_RANDOM_DRAW), null, 0);
    }

    static Character shenhe() {
        return new Shenhe(new FavoniusLance(5, () -> FIXED_RANDOM_DRAW), null, 0);
    }

    static Character sangonomiyaKokomi() {
        return new SangonomiyaKokomi(
                new WanderingEvenstar(5), null, TalentDataManager.getInstance(), 0,
                () -> FIXED_RANDOM_DRAW);
    }

    static Character kaedeharaKazuha() {
        return new KaedeharaKazuha(new SkywardBlade(), null, 0);
    }

    static Character gaming() {
        return new Gaming(new FavoniusGreatsword(5, () -> FIXED_RANDOM_DRAW), null, 0);
    }

    static Character rosaria() {
        return new Rosaria(new FavoniusLance(5, () -> FIXED_RANDOM_DRAW), null, 0);
    }

    static Character xianyun() {
        return new Xianyun(new FavoniusCodex(5, () -> FIXED_RANDOM_DRAW), null, 0);
    }

    static Character layla() {
        return new Layla(
                new FavoniusSword(5, () -> FIXED_RANDOM_DRAW), null, TalentDataManager.getInstance(), 0,
                () -> FIXED_RANDOM_DRAW);
    }

    static Character ningguang() {
        return new Ningguang(new WanderingEvenstar(5), null, 0);
    }

    static Character ororon() {
        return new Ororon(new TheStringless(5), null, 0);
    }

    static Character kirara() {
        return new Kirara(new WolfFang(), null, 0);
    }

    static Character xingqiu() {
        return new Xingqiu(new SacrificialSword(5, () -> FIXED_RANDOM_DRAW), null);
    }

    static Character xiangling() {
        return new Xiangling(new FavoniusLance(5, () -> FIXED_RANDOM_DRAW), null);
    }

    static Character sucrose() {
        return new Sucrose(new ThrillingTalesOfDragonSlayers(5), null, () -> 4.0);
    }

    static Character zhongli() {
        return new Zhongli(new Deathmatch(1), null, TalentDataManager.getInstance(), 0,
                () -> FIXED_RANDOM_DRAW);
    }

    static Character nahida() {
        return new Nahida(new WanderingEvenstar(5), null, 0);
    }

    static Character navia() {
        return new Navia(
                new Verdict(1), null, TalentDataManager.getInstance(), 0,
                () -> FIXED_RANDOM_DRAW);
    }

    static Character chiori() {
        return new Chiori(
                new UrakuMisugiri(1), null, TalentDataManager.getInstance(), 0,
                () -> FIXED_RANDOM_DRAW);
    }

    static Character kukiShinobu() {
        return new KukiShinobu(new WolfFang(), null, TalentDataManager.getInstance(), 0,
                () -> FIXED_RANDOM_DRAW);
    }

    static Character keqing() {
        return new Keqing(new WolfFang(), null, 0);
    }

    static Character faruzan() {
        return new Faruzan(new FavoniusWarbow(5, () -> FIXED_RANDOM_DRAW), null, 6);
    }

    static Character faruzanC0() {
        return new Faruzan(new FavoniusWarbow(5, () -> FIXED_RANDOM_DRAW), null, 0);
    }

    static Character bennett() {
        return new Bennett(new SkywardBlade(), null);
    }

    static Character beidou() {
        return new Beidou(
                new FavoniusGreatsword(5, () -> FIXED_RANDOM_DRAW), null, 6);
    }

    static Character collei() {
        return new Collei(
                new FavoniusWarbow(5, () -> FIXED_RANDOM_DRAW), null, 6);
    }

    static Character fischl() {
        return new Fischl(new TheStringless(), null);
    }

    static Character fischlFavonius() {
        return new Fischl(new FavoniusWarbow(5, () -> FIXED_RANDOM_DRAW), null);
    }

    static Character chevreuse() {
        return new Chevreuse(new Deathmatch(1), null, TalentDataManager.getInstance(), 0,
                () -> FIXED_RANDOM_DRAW);
    }

    static Character chevreuseFavonius() {
        return new Chevreuse(
                new FavoniusLance(5, () -> FIXED_RANDOM_DRAW),
                null,
                TalentDataManager.getInstance(),
                0,
                () -> FIXED_RANDOM_DRAW);
    }

    static Character yaeMiko() {
        return new YaeMiko(new WanderingEvenstar(5), null, 0);
    }

    static Character yoimiya() {
        return new Yoimiya(new Rust(1), null, 0);
    }
}
