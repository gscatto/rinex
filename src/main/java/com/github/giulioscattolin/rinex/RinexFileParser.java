package com.github.giulioscattolin.rinex;

import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.Double.isNaN;

public class RinexFileParser {
    private final RinexFileCollector itsFileCollector;
    private LineReader itsLineReader = new VersionType();
    private Supplier<LineReader> itsLineReaderSupplier;
    RinexNavigationDataBuilder itsNavigationMessageBuilder;
    private MutableRinexFile itsMutableRinexFile = new MutableRinexFile();

    public RinexFileParser(RinexFileCollector fileCollector) {
        itsFileCollector = fileCollector;
    }

    public void readLine(String line) {
        itsLineReader.readLine(line);
    }

    public void flush() {
        itsFileCollector.collect(itsMutableRinexFile);
    }

    class VersionType extends LineReader {
        protected void execute() {
            if (isHeader("RINEX VERSION / TYPE"))
                parseLine();
        }

        private void parseLine() {
            String version = itsLine.substring(0, 9).trim();
            char fileType = itsLine.charAt(20);
            char satelliteSystem = itsLine.charAt(40);
            switch (version) {
                case "2":
                    switch (fileType) {
                        case 'N':
                            itsLineReader = new NavigationHeader();
                            itsLineReaderSupplier = () ->
                                new SvEpochSvClk(
                                    new SvEpochSvClkReaderV2('G'),
                                    (ignored) -> new RinexGpsNavigationDataBuilderV2(),
                                    () -> new BroadcastOrbit(new BroadcastOrbitParameterReaderV2()));
                            return;
                    }
                case "2.10":
                    switch (fileType) {
                        case 'N':
                            itsLineReader = new NavigationHeader();
                            itsLineReaderSupplier = () ->
                                new SvEpochSvClk(
                                    new SvEpochSvClkReaderV2('G'),
                                    (ignored) -> new RinexGpsNavigationDataBuilderV210(),
                                    () -> new BroadcastOrbit(new BroadcastOrbitParameterReaderV2()));
                            return;
                    }
                case "3.02":
                case "3.03":
                case "3.04":
                    switch (fileType) {
                        case 'N':
                            switch (satelliteSystem) {
                                case 'G':
                                    itsLineReader = new NavigationHeader();
                                    itsLineReaderSupplier = () ->
                                        new SvEpochSvClk(
                                            new SvEpochSvClkReaderV3(),
                                            (ignored) -> new RinexGpsNavigationDataBuilderV302(),
                                            () -> new BroadcastOrbit(new BroadcastOrbitParameterReaderV3()));
                                    return;
                            }
                    }
            }
        }
    }

    class NavigationHeader extends LineReader {
        protected void execute() {
            if (!findPgmRunByDate())
                findEndOfHeader();
        }

        private boolean findPgmRunByDate() {
            return false;
        }

        private void findEndOfHeader() {
            if (isHeaderLabelEqualTo("END OF HEADER"))
                itsLineReader = itsLineReaderSupplier.get();
        }
    }

    class SvEpochSvClk extends LineReader {
        private final SvEpochSvClkReader itsSvEpochSvClkReader;
        private final Function<Character, RinexNavigationDataBuilder> itsNavigationMessageBuilderSupplier;
        private final Supplier<LineReader> itsBroadcastOrbitReaderSupplier;

        SvEpochSvClk(SvEpochSvClkReader svEpochSvClkReader, Function<Character, RinexNavigationDataBuilder> navigationMessageBuilderSupplier, Supplier<LineReader> broadcastOrbitCompilerSupplier) {
            itsSvEpochSvClkReader = svEpochSvClkReader;
            itsNavigationMessageBuilderSupplier = navigationMessageBuilderSupplier;
            itsBroadcastOrbitReaderSupplier = broadcastOrbitCompilerSupplier;
        }

        protected void execute() {
            if (isHeader("RINEX VERSION / TYPE")) {
                new VersionType().readLine(itsLine);
                return;
            }
            whichSatelliteSystem();
        }

        private void whichSatelliteSystem() {
            char satelliteSystem = itsSvEpochSvClkReader.getSatelliteSystem(itsLine);
            itsNavigationMessageBuilder = itsNavigationMessageBuilderSupplier.apply(satelliteSystem);
            itsLineReader = itsBroadcastOrbitReaderSupplier.get();
            findPrn();
        }

        private void findPrn() {
            int prn = itsSvEpochSvClkReader.getSatelliteNumberOrNegative(itsLine);
            if (prn < 0)
                return;
            itsNavigationMessageBuilder.setParameter(0, prn);
            findToc();
        }

        private void findToc() {
            int year = itsSvEpochSvClkReader.getTocYearOrNegative(itsLine);
            int month = itsSvEpochSvClkReader.getTocMonthOrNegative(itsLine);
            int day = itsSvEpochSvClkReader.getTocDayOrNegative(itsLine);
            int hour = itsSvEpochSvClkReader.getTocHourOrNegative(itsLine);
            int minute = itsSvEpochSvClkReader.getTocMinuteOrNegative(itsLine);
            int second = itsSvEpochSvClkReader.getTocSecondOrNegative(itsLine);
            if (year < 0 || month < 0 || day < 0 || hour < 0 || minute < 0 || second < 0)
                return;
            itsNavigationMessageBuilder.setParameter(1, year);
            itsNavigationMessageBuilder.setParameter(2, month);
            itsNavigationMessageBuilder.setParameter(3, day);
            itsNavigationMessageBuilder.setParameter(4, hour);
            itsNavigationMessageBuilder.setParameter(5, minute);
            itsNavigationMessageBuilder.setParameter(6, second);
            findSvClock();
        }

        private void findSvClock() {
            double first = itsSvEpochSvClkReader.getFirstTimeParameterOrNaN(itsLine);
            double second = itsSvEpochSvClkReader.getSecondTimeParameterOrNaN(itsLine);
            double third = itsSvEpochSvClkReader.getThirdTimeParameterOrNaN(itsLine);
            if (isNaN(first) || isNaN(second) || isNaN(third))
                return;
            itsNavigationMessageBuilder.setParameter(7, first);
            itsNavigationMessageBuilder.setParameter(8, second);
            itsNavigationMessageBuilder.setParameter(9, third);
        }
    }

    private class BroadcastOrbit extends LineReader {
        private final BroadcastOrbitParameterReader itsParameterReader;
        private int itsIndex = 10;
        private boolean shouldContinue = true;

        private BroadcastOrbit(BroadcastOrbitParameterReader parameterReader) {
            itsParameterReader = parameterReader;
        }

        protected void execute() {
            for (int i = 0; i < 4 && shouldContinue; i++)
                readParameter(i);
        }

        private void readParameter(int i) {
            double value = itsParameterReader.getParameterOrNaN(itsLine, i);
            tellIfShouldContinue(value);
            if (shouldContinue) {
                itsNavigationMessageBuilder.setParameter(itsIndex++, value);
                collectIfBuilderIsReady();
            }
        }

        private void collectIfBuilderIsReady() {
            if (itsNavigationMessageBuilder.isReady()) {
                itsMutableRinexFile.addRecord(itsNavigationMessageBuilder.build());
                itsLineReader = itsLineReaderSupplier.get();
                shouldContinue = false;
            }
        }

        private void tellIfShouldContinue(double value) {
            if (isNaN(value)) {
                itsLineReader = itsLineReaderSupplier.get();
                shouldContinue = false;
            }
        }
    }
}