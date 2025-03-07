/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.hive;

import com.facebook.presto.hive.OrcFileWriterConfig.StreamLayoutType;
import com.facebook.presto.orc.OrcWriterOptions;
import com.facebook.presto.orc.metadata.DwrfStripeCacheMode;
import com.facebook.presto.orc.writer.StreamLayoutFactory.ColumnSizeLayoutFactory;
import com.facebook.presto.orc.writer.StreamLayoutFactory.StreamSizeLayoutFactory;
import com.google.common.collect.ImmutableMap;
import io.airlift.units.DataSize;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static com.facebook.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static com.facebook.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static com.facebook.airlift.configuration.testing.ConfigAssertions.recordDefaults;
import static com.facebook.presto.hive.OrcFileWriterConfig.StreamLayoutType.BY_COLUMN_SIZE;
import static com.facebook.presto.hive.OrcFileWriterConfig.StreamLayoutType.BY_STREAM_SIZE;
import static com.facebook.presto.orc.metadata.DwrfStripeCacheMode.FOOTER;
import static com.facebook.presto.orc.metadata.DwrfStripeCacheMode.INDEX;
import static com.facebook.presto.orc.metadata.DwrfStripeCacheMode.INDEX_AND_FOOTER;
import static io.airlift.units.DataSize.Unit.BYTE;
import static io.airlift.units.DataSize.Unit.KILOBYTE;
import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.lang.Math.toIntExact;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertTrue;

public class TestOrcFileWriterConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(OrcFileWriterConfig.class)
                .setStripeMinSize(new DataSize(32, MEGABYTE))
                .setStripeMaxSize(new DataSize(64, MEGABYTE))
                .setStripeMaxRowCount(10_000_000)
                .setRowGroupMaxRowCount(10_000)
                .setDictionaryMaxMemory(new DataSize(16, MEGABYTE))
                .setStringStatisticsLimit(new DataSize(64, BYTE))
                .setMaxCompressionBufferSize(new DataSize(256, KILOBYTE))
                .setStreamLayoutType(BY_COLUMN_SIZE)
                .setDwrfStripeCacheEnabled(false)
                .setDwrfStripeCacheMaxSize(new DataSize(8, MEGABYTE))
                .setDwrfStripeCacheMode(INDEX_AND_FOOTER)
                .setCompressionLevel(Integer.MIN_VALUE)
                .setIntegerDictionaryEncodingEnabled(false)
                .setStringDictionaryEncodingEnabled(true)
                .setStringDictionarySortingEnabled(true)
                .setFlatMapWriterEnabled(false));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("hive.orc.writer.stripe-min-size", "13MB")
                .put("hive.orc.writer.stripe-max-size", "27MB")
                .put("hive.orc.writer.stripe-max-rows", "44")
                .put("hive.orc.writer.row-group-max-rows", "11")
                .put("hive.orc.writer.dictionary-max-memory", "13MB")
                .put("hive.orc.writer.string-statistics-limit", "17MB")
                .put("hive.orc.writer.max-compression-buffer-size", "19MB")
                .put("hive.orc.writer.stream-layout-type", "BY_STREAM_SIZE")
                .put("hive.orc.writer.dwrf-stripe-cache-enabled", "true")
                .put("hive.orc.writer.dwrf-stripe-cache-max-size", "10MB")
                .put("hive.orc.writer.dwrf-stripe-cache-mode", "FOOTER")
                .put("hive.orc.writer.compression-level", "5")
                .put("hive.orc.writer.integer-dictionary-encoding-enabled", "true")
                .put("hive.orc.writer.string-dictionary-encoding-enabled", "false")
                .put("hive.orc.writer.string-dictionary-sorting-enabled", "false")
                .put("hive.orc.writer.flat-map-writer-enabled", "true")
                .build();

        OrcFileWriterConfig expected = new OrcFileWriterConfig()
                .setStripeMinSize(new DataSize(13, MEGABYTE))
                .setStripeMaxSize(new DataSize(27, MEGABYTE))
                .setStripeMaxRowCount(44)
                .setRowGroupMaxRowCount(11)
                .setDictionaryMaxMemory(new DataSize(13, MEGABYTE))
                .setStringStatisticsLimit(new DataSize(17, MEGABYTE))
                .setMaxCompressionBufferSize(new DataSize(19, MEGABYTE))
                .setStreamLayoutType(BY_STREAM_SIZE)
                .setDwrfStripeCacheEnabled(true)
                .setDwrfStripeCacheMaxSize(new DataSize(10, MEGABYTE))
                .setDwrfStripeCacheMode(FOOTER)
                .setCompressionLevel(5)
                .setIntegerDictionaryEncodingEnabled(true)
                .setStringDictionaryEncodingEnabled(false)
                .setStringDictionarySortingEnabled(false)
                .setFlatMapWriterEnabled(true);

        assertFullMapping(properties, expected);
    }

    @Test
    public void testWithNoOptionsSet()
    {
        OrcFileWriterConfig config = new OrcFileWriterConfig();
        // should succeed.
        config.toOrcWriterOptionsBuilder().build();
    }

    @Test
    public void testOrcWriterOptionsBuilder()
    {
        DataSize stripeMinSize = new DataSize(10, MEGABYTE);
        DataSize stripeMaxSize = new DataSize(50, MEGABYTE);
        int stripeMaxRowCount = 1_000_000;
        int rowGroupMaxRowCount = 15_000;
        DataSize dictionaryMaxMemory = new DataSize(20, MEGABYTE);
        DataSize stringStatisticsLimit = new DataSize(32, BYTE);
        DataSize maxCompressionBufferSize = new DataSize(512, KILOBYTE);
        StreamLayoutType streamLayoutType = BY_STREAM_SIZE;
        DataSize dwrfStripeCacheMaxSize = new DataSize(4, MEGABYTE);
        DwrfStripeCacheMode dwrfStripeCacheMode = INDEX;
        int compressionLevel = 5;
        boolean flatMapWriterEnabled = true;

        OrcFileWriterConfig config = new OrcFileWriterConfig()
                .setStripeMinSize(stripeMinSize)
                .setStripeMaxSize(stripeMaxSize)
                .setStripeMaxRowCount(stripeMaxRowCount)
                .setRowGroupMaxRowCount(rowGroupMaxRowCount)
                .setDictionaryMaxMemory(dictionaryMaxMemory)
                .setStringStatisticsLimit(stringStatisticsLimit)
                .setMaxCompressionBufferSize(maxCompressionBufferSize)
                .setStreamLayoutType(streamLayoutType)
                .setDwrfStripeCacheEnabled(false)
                .setDwrfStripeCacheMaxSize(dwrfStripeCacheMaxSize)
                .setDwrfStripeCacheMode(dwrfStripeCacheMode)
                .setCompressionLevel(5)
                .setFlatMapWriterEnabled(flatMapWriterEnabled);

        assertEquals(stripeMinSize, config.getStripeMinSize());
        assertEquals(stripeMaxSize, config.getStripeMaxSize());
        assertEquals(stripeMaxRowCount, config.getStripeMaxRowCount());
        assertEquals(rowGroupMaxRowCount, config.getRowGroupMaxRowCount());
        assertEquals(dictionaryMaxMemory, config.getDictionaryMaxMemory());
        assertEquals(stringStatisticsLimit, config.getStringStatisticsLimit());
        assertEquals(maxCompressionBufferSize, config.getMaxCompressionBufferSize());
        assertEquals(streamLayoutType, config.getStreamLayoutType());
        assertFalse(config.isDwrfStripeCacheEnabled());
        assertEquals(dwrfStripeCacheMaxSize, config.getDwrfStripeCacheMaxSize());
        assertEquals(dwrfStripeCacheMode, config.getDwrfStripeCacheMode());
        assertEquals(compressionLevel, config.getCompressionLevel());
        assertEquals(flatMapWriterEnabled, config.isFlatMapWriterEnabled());

        assertNotSame(config.toOrcWriterOptionsBuilder(), config.toOrcWriterOptionsBuilder());
        OrcWriterOptions options = config.toOrcWriterOptionsBuilder().build();

        assertEquals(toIntExact(stripeMinSize.toBytes()), options.getFlushPolicy().getStripeMinBytes());
        assertEquals(toIntExact(stripeMaxSize.toBytes()), options.getFlushPolicy().getStripeMaxBytes());
        assertEquals(stripeMaxRowCount, options.getFlushPolicy().getStripeMaxRowCount());
        assertEquals(rowGroupMaxRowCount, options.getRowGroupMaxRowCount());
        assertEquals(dictionaryMaxMemory, options.getDictionaryMaxMemory());
        assertEquals(stringStatisticsLimit, options.getMaxStringStatisticsLimit());
        assertEquals(maxCompressionBufferSize, options.getMaxCompressionBufferSize());
        assertTrue(options.getStreamLayoutFactory() instanceof StreamSizeLayoutFactory);
        assertEquals(Optional.empty(), options.getDwrfStripeCacheOptions());
        assertEquals(OptionalInt.of(compressionLevel), options.getCompressionLevel());
    }

    @Test
    public void testStreamLayoutOption()
    {
        OrcFileWriterConfig config = new OrcFileWriterConfig();

        config.setStreamLayoutType(BY_STREAM_SIZE);
        OrcWriterOptions options = config.toOrcWriterOptionsBuilder().build();
        assertTrue(options.getStreamLayoutFactory() instanceof StreamSizeLayoutFactory);

        config.setStreamLayoutType(BY_COLUMN_SIZE);
        options = config.toOrcWriterOptionsBuilder().build();
        assertTrue(options.getStreamLayoutFactory() instanceof ColumnSizeLayoutFactory);
    }

    @Test
    public void testDefaultCompressionLevel()
    {
        OrcFileWriterConfig config = new OrcFileWriterConfig();
        OrcWriterOptions options = config.toOrcWriterOptionsBuilder().build();

        assertEquals(OptionalInt.empty(), options.getCompressionLevel());
    }
}
