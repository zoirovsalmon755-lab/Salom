package com.viltrumite.boss;

import com.viltrumite.boss.entity.ViltrumiteEntity;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Ҳамаи гапҳои босс дар чат. Матнҳоро озодона иваз кунед ё илова намоед. */
public final class Taunts {
	public enum Type {
		SPAWN, CHASE, FLEE, HIT, BOSS_HIT, BLOCK, GRAB, THROW, BLOCKS, CLONES,
		DASH, SLAM, PLAYER_LOW, PLAYER_DEAD, ENRAGE, DEATH
	}

	private static final Map<Type, String[]> LINES = new EnumMap<>(Type.class);

	static {
		LINES.put(Type.SPAWN, new String[] {
				"Ниҳоят омадӣ... Ман ин рӯзро кайҳо интизор будам.",
				"Ту ҷуръат кардӣ, ки бо ман рӯ ба рӯ шавӣ? Хубтар мешуд, ки хона мемондӣ.",
				"Як одами оддӣ... Ин ҷанг дер давом намекунад."
		});
		LINES.put(Type.CHASE, new String[] {
				"Бигузор бубинам, ки то куҷо истодагарӣ мекунӣ!",
				"Ин ҳама чизест, ки дорӣ?",
				"Ту ҳатто ба ман ҳамлаи дуруст карда наметавонӣ!",
				"Заиф... заифтар аз он ки гумон доштам.",
				"Ҳар қадаме, ки мегузорӣ, туро ба марг наздиктар мекунад."
		});
		LINES.put(Type.FLEE, new String[] {
				"Чӣ шуд, тарсидӣ?! Ҳа-ҳа-ҳа!",
				"Ҳой! Ба куҷо мегурезӣ? Гурехтан фоида надорад!",
				"Бигурез, бигурез... Ман ҳамаҷо туро меёбам.",
				"Тарсончак! Баргард ва мисли мард ҷанг кун!",
				"Ту аз ман пинҳон шуда наметавонӣ!"
		});
		LINES.put(Type.HIT, new String[] {
				"Ин ҳанӯз танҳо оғози кор аст.",
				"Дард мекунад? Хуб.",
				"Бархез... ман ҳанӯз тамом накардаам.",
				"Ҳамин тавр бояд бошад!"
		});
		LINES.put(Type.BOSS_HIT, new String[] {
				"Ин ҳама зӯри туст?",
				"Ту маро ба хандаи ман овардӣ.",
				"Хуруши магас барин...",
				"Боз кун, шояд дарди маро ҳис кунам."
		});
		LINES.put(Type.BLOCK, new String[] {
				"Ин ба ман кор намекунад.",
				"Хеле суст!",
				"Ҳамлаи ту ба ман намерасад.",
				"Мебинам, ки чӣ мекунӣ — пештар аз он ки кунӣ."
		});
		LINES.put(Type.GRAB, new String[] {
				"Биё, ту ҳам ба осмон парвоз кун!",
				"Ба боло — ҳамроҳи ман!",
				"Ҳоло ба ту нишон медиҳам, ки парвоз чист!"
		});
		LINES.put(Type.THROW, new String[] {
				"Ба замин афт!",
				"Ин барои он, ки ҷуръат кардӣ!",
				"Хуш омадӣ ба замин!"
		});
		LINES.put(Type.BLOCKS, new String[] {
				"Ҳатто замин бар зидди ту аст!",
				"Бинигар, ки ман бо як ишора чӣ кор карда метавонам.",
				"Ба ту чизи вазнине мефиристам!"
		});
		LINES.put(Type.CLONES, new String[] {
				"Ман танҳо нестам.",
				"Акнун аз ҳар тараф ман ҳастам!",
				"Кадомаш ман ҳастам? Ҳа-ҳа!"
		});
		LINES.put(Type.DASH, new String[] {
				"Ту маро дида наметавонӣ.",
				"Хеле суст ҳастӣ!",
				"Ман дар пушти туам!"
		});
		LINES.put(Type.SLAM, new String[] {
				"Замин ларзад!",
				"Ин зарба ҳатто кӯҳҳоро мешиканад!"
		});
		LINES.put(Type.PLAYER_LOW, new String[] {
				"Чанд лаҳзаи дигар... ва ҳама чиз тамом мешавад.",
				"Ҷонат тамом шуда истодааст. Ҳис мекунӣ?",
				"Ҳоло гуфтан мехоҳӣ, ки бас аст?"
		});
		LINES.put(Type.PLAYER_DEAD, new String[] {
				"Нотавон... Ман бештар интизор будам.",
				"Оё ин буд? Дилгиркунанда.",
				"Дигаре ҳаст, ки бо ман ҷанг кунад?"
		});
		LINES.put(Type.ENRAGE, new String[] {
				"Чӣ?! Ту маро захмӣ кардӣ?! Акнун ҷиддӣ мешавад!",
				"Бас! Дигар бозӣ нест! Ҳоло ҳақиқати зӯри маро мебинӣ!"
		});
		LINES.put(Type.DEATH, new String[] {
				"Ту... ғолиб шудӣ... Ин имконнопазир аст...",
				"Ман... шикаст хӯрдам?.. Хуб... ту сазовор будӣ..."
		});
	}

	public static void say(ServerWorld world, ViltrumiteEntity boss, Type type) {
		String[] lines = LINES.get(type);
		if (lines == null || lines.length == 0) {
			return;
		}
		String line = lines[boss.getRandom().nextInt(lines.length)];
		Text msg = Text.literal("[Витрумит] ").formatted(Formatting.GOLD, Formatting.BOLD)
				.append(Text.literal(line).formatted(Formatting.YELLOW));
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (p.squaredDistanceTo(boss) < 64 * 64) {
				p.sendMessage(msg, false);
			}
		}
	}

	private Taunts() {}
}
