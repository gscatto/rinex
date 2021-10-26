package com.github.giulioscattolin.rinex;

import static com.github.giulioscattolin.rinex.RinexTokenizerProvider.TokenizerName.SV_EPOCH_SV_CLK_V304;

class NavigationMessageFileHeaderV304Tokenizer extends RinexTokenizerTemplate {
    public NavigationMessageFileHeaderV304Tokenizer(RinexTokenCollector tokenCollector, RinexTokenizerProvider tokenizerProvider, RinexTokenizerDriver tokenDriver) {
        super(tokenCollector, tokenizerProvider, tokenDriver);
    }

    protected void readLine() {
        switch (getHeaderLabel()) {
            case "PGM / RUN BY / DATE":
                tokenizePgmRunByDate();
                return;
            case "END OF HEADER":
                selectTokenizer(SV_EPOCH_SV_CLK_V304);
                return;
        }
        error("while reading headers: unknown header \"" + getHeaderLabel() + "\"");
    }
}
