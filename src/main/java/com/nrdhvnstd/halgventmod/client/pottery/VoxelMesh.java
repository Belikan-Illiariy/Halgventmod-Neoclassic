package com.nrdhvnstd.halgventmod.client.pottery;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;

import com.nrdhvnstd.halgventmod.pottery.Vessel;
import com.nrdhvnstd.halgventmod.pottery.VesselGeometry;

/**
 * Видимые грани вокселей сосуда: грань рисуется, если соседа с той стороны нет. Грань — число
 * {@code индекс_вокселя * 6 + сторона}. Сетки кешируются по форме: одна и та же посудина строится один раз.
 */
public final class VoxelMesh {
    // Стороны и порядок углов (против часовой стрелки, если смотреть снаружи)
    public static final int UP = 0, DOWN = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;
    private static final int[][] STEP = {{0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    /** Углы грани: смещения от угла вокселя (x, y, z) по сторонам. */
    public static final int[][][] CORNERS = {
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},   // верх
            {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},   // низ
            {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}},   // север (−z)
            {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},   // юг (+z)
            {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}},   // запад (−x)
            {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}}};  // восток (+x)
    public static final float[][] NORMALS = {{0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    /** Освещённость сторон без света мира (окно круга). */
    public static final float[] SHADE = {1.0F, 0.5F, 0.8F, 0.8F, 0.62F, 0.62F};

    private static final int CACHE = 256;
    private static final Map<Vessel, int[]> CACHED = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Vessel, int[]> eldest) {
            return size() > CACHE;
        }
    };

    private VoxelMesh() {}

    public static synchronized int[] of(Vessel vessel) {
        return CACHED.computeIfAbsent(vessel, v -> build(VesselGeometry.voxels(v.profile(), v.details())));
    }

    public static int[] build(BitSet voxels) {
        int[] faces = new int[voxels.cardinality() * 6];
        int n = 0;
        for (int i = voxels.nextSetBit(0); i >= 0; i = voxels.nextSetBit(i + 1)) {
            int x = VesselGeometry.x(i), y = VesselGeometry.y(i), z = VesselGeometry.z(i);
            for (int side = 0; side < 6; side++) {
                int nx = x + STEP[side][0], ny = y + STEP[side][1], nz = z + STEP[side][2];
                if (!VesselGeometry.inGrid(nx, ny, nz) || !voxels.get(VesselGeometry.index(nx, ny, nz))) {
                    faces[n++] = i * 6 + side;
                }
            }
        }
        return java.util.Arrays.copyOf(faces, n);
    }
}
