/* global L, LocationLabBridge */
"use strict";

const mapPreview = (() => {
    const initialZoom = 16;
    const tileOptions = { minZoom: 3, maxZoom: 18, noWrap: true };
    const pin = (color, width, height) => L.divIcon({
        className: "selection-pin",
        iconSize: [width, height],
        iconAnchor: [width / 2, height - 1],
        html: '<svg width="' + width + '" height="' + height + '" viewBox="0 0 24 28" aria-hidden="true">' +
            '<path fill="' + color + '" fill-rule="evenodd" stroke="#fff" stroke-width="1" stroke-linejoin="round"' +
            ' d="M12,27.6 C10,25 1.7,17 1.7,11 A10.3,10.3 0,1 1,22.3 11 C22.3,17 14,25 12,27.6 Z' +
            ' M12,7 A3.7,3.7 0,1 0,12 14.4 A3.7,3.7 0,1 0,12 7 Z"/></svg>',
    });
    let map;
    let marker;
    let range;
    let currentMarker;
    let previousMarker;
    let previewRadius = 0;

    function initialize(source, editable, focus, viewport, showAttribution) {
        let crs;
        let tiles;
        switch (source) {
            case "BaiduPreview":
                crs = L.ChineseCRS.CRS_BaiduMercator;
                tiles = new L.ChineseCRS.BaiduTileLayer(
                    "https://maponline0.bdimg.com/tile/?qt=vtile&x={x}&y={y}&z={z}&styles=pl&scaler=1&showtext=1",
                    { ...tileOptions, attribution: "© 百度地图" },
                );
                break;
            case "GooglePreview":
                crs = L.CRS.EPSG3857;
                tiles = L.tileLayer(
                    "https://mt1.google.com/vt?lyrs=m&x={x}&y={y}&z={z}",
                    { ...tileOptions, attribution: "© Google" },
                );
                break;
            case "TencentPreview":
                crs = L.ChineseCRS.CRS_GCJ02_3857;
                tiles = L.tileLayer(
                    "https://rt0.map.gtimg.com/tile?z={z}&x={x}&y={-y}&styleid=1&version=297",
                    { ...tileOptions, attribution: "© 腾讯地图" },
                );
                break;
            case "AMapPreview":
                crs = L.ChineseCRS.CRS_GCJ02_3857;
                tiles = L.tileLayer(
                    "https://wprd01.is.autonavi.com/appmaptile?x={x}&y={y}&z={z}&lang=zh_cn&size=1&scl=1&style=7",
                    { ...tileOptions, attribution: "© 高德地图" },
                );
                break;
        }
        map = L.map("map", {
            crs,
            zoomControl: false,
            attributionControl: showAttribution,
            dragging: editable,
            inertia: editable,
            inertiaDeceleration: 2300,
            inertiaMaxSpeed: 2300,
            zoomSnap: editable ? 1 : 0,
            minZoom: tileOptions.minZoom,
            maxZoom: tileOptions.maxZoom,
            touchZoom: editable,
            doubleClickZoom: editable,
            scrollWheelZoom: editable,
            boxZoom: editable,
            keyboard: editable,
        }).setView(viewport ? viewport.slice(0, 2) : focus, viewport ? viewport[2] : initialZoom);
        map.attributionControl?.setPrefix(false);

        let loaded = false;
        tiles.once("tileload", () => {
            loaded = true;
            LocationLabBridge.tiles(true);
        });
        tiles.on("load", () => {
            if (!loaded) LocationLabBridge.tiles(false);
        });
        tiles.addTo(map);

        const markerOptions = { interactive: false, keyboard: false };
        marker = L.marker(focus, { ...markerOptions,
            icon: editable ? pin("#087D76", 34, 38) : pin("#087D76", 23, 26) }).addTo(map);
        currentMarker = L.marker(focus, { ...markerOptions, icon: pin("#236DBB", 16, 18), zIndexOffset: 2000 });
        previousMarker = L.marker(focus, { ...markerOptions, icon: pin("#687977", 16, 18), zIndexOffset: 1000 });
        range = L.circle(focus, {
            radius: 0, interactive: false, color: "#087D76", weight: 2, fillOpacity: 0.14,
        });
        if (editable) {
            map.on("click", ({ latlng }) => {
                const point = latlng.wrap();
                LocationLabBridge.pick(point.lat, point.lng);
            });
            map.on("moveend", () => {
                const center = map.getCenter().wrap();
                LocationLabBridge.viewport(center.lat, center.lng, map.getZoom());
            });
        }
        new ResizeObserver(() => {
            map.invalidateSize({ pan: editable, animate: false });
            if (!editable) fitPreview();
        }).observe(document.getElementById("map"));
    }

    function fitPreview() {
        const size = map.getSize();
        if (size.x <= 32 || size.y <= 32) return;
        if (previewRadius > 0) {
            map.fitBounds(range.getBounds(), { padding: [16, 16], maxZoom: 18, animate: false });
        } else {
            map.setView(marker.getLatLng(), initialZoom, { animate: false });
        }
    }

    function updateMarker(layer, point) {
        if (point) layer.setLatLng(point).addTo(map);
        else layer.remove();
    }

    return {
        render(source, editable, latitude, longitude, radiusMeters, recenter, zoomDelta, currentPoint, previousPoint,
            viewport, showAttribution, showCenter) {
            const focus = L.latLng(latitude, longitude);
            const firstRender = !map;
            if (firstRender) initialize(source, editable, focus, viewport, showAttribution);
            else if (recenter) {
                map.invalidateSize({ pan: false });
                map.setView(focus, Math.max(initialZoom, map.getZoom()), { reset: true });
            }
            if (zoomDelta !== 0) map.setZoom(map.getZoom() + zoomDelta);
            if (firstRender || !marker.getLatLng().equals(focus, 0) || previewRadius !== radiusMeters) {
                marker.setLatLng(focus);
                if (radiusMeters > 0) range.setLatLng(focus).setRadius(radiusMeters).addTo(map);
                else range.remove();
                previewRadius = radiusMeters;
                if (!editable) fitPreview();
            }
            if (showCenter) marker.addTo(map);
            else marker.remove();
            updateMarker(previousMarker, previousPoint);
            updateMarker(currentMarker, currentPoint);
        },
    };
})();
