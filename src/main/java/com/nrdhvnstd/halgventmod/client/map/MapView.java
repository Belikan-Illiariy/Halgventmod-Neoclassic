package com.nrdhvnstd.halgventmod.client.map;

/** Перевод координат открытой карты: блоки мира ↔ пиксели экрана. Даёт MapScreen. */
interface MapView {
    float screenX(double worldX);

    float screenY(double worldZ);

    double worldX(double screenX);

    double worldZ(double screenY);

    float zoom();
}
