package dev.justin.infinium;

import com.greymerk.roguelike.dungeon.Dungeon;
import com.greymerk.roguelike.dungeon.room.IRoom;
import com.greymerk.roguelike.editor.Coord;
import com.greymerk.roguelike.editor.boundingbox.IBounded;
import com.greymerk.roguelike.state.RoguelikeState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

/** Only class that touches Roguelike Dungeons (roguelike-dungeons-fabric 2.1.2+mc26.2). Loaded only when that mod is present. */
final class RoguelikeBridge {
    record Room(int y, int x0, int y0, int z0, int x1, int y1, int z1) {}
    record Snap(int x, int z, boolean generated, List<Room> rooms) {}

    @SuppressWarnings("unchecked")
    static List<Snap> poll(ServerLevel level) throws Exception {
        RoguelikeState st = RoguelikeState.getServerState(level.dimension(), level.getServer());
        java.lang.reflect.Field f = RoguelikeState.class.getDeclaredField("dungeons");
        f.setAccessible(true);
        List<Dungeon> ds = new ArrayList<>((List<Dungeon>) f.get(st));
        List<Snap> out = new ArrayList<>();
        for (Dungeon d : ds) {
            Coord o = d.getPos();
            List<Room> rooms = new ArrayList<>();
            if (d.hasLayout()) for (IRoom r : d) {
                Coord w = r.getWorldPos();
                if (w == null) continue;
                var bb = r.getBoundingBox();
                if (bb.isPresent()) {
                    IBounded b = bb.get(); Coord s = b.getStart(), e = b.getEnd();
                    rooms.add(new Room(w.getY(), Math.min(s.getX(), e.getX()), Math.min(s.getY(), e.getY()), Math.min(s.getZ(), e.getZ()),
                        Math.max(s.getX(), e.getX()), Math.max(s.getY(), e.getY()), Math.max(s.getZ(), e.getZ())));
                } else rooms.add(new Room(w.getY(), w.getX() - 4, w.getY() - 1, w.getZ() - 4, w.getX() + 4, w.getY() + 6, w.getZ() + 4));
            }
            out.add(new Snap(o.getX(), o.getZ(), d.isGenerated(), rooms));
        }
        return out;
    }
    private RoguelikeBridge() {}
}
