package com.ddareungi.data.station.application;

import com.ddareungi.data.station.infrastructure.SeoulBikeApiClient;
import com.ddareungi.data.station.infrastructure.StationSnapshotWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StationSnapshotService {
    private final SeoulBikeApiClient client;
    private final StationSnapshotWriter writer;

    public StationSnapshotService(SeoulBikeApiClient client, StationSnapshotWriter writer) {
        this.client = client;
        this.writer = writer;
    }

    @Transactional
    public int collect() {
        var snapshots = client.fetchAll();
        writer.upsert(snapshots);
        return snapshots.size();
    }
}
