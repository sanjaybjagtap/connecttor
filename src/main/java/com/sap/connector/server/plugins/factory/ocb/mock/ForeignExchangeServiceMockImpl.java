package com.sap.connector.server.plugins.factory.ocb.mock;

import com.ffusion.beans.common.Currency;
import com.ffusion.beans.fx.FXCurrencies;
import com.ffusion.beans.fx.FXRate;
import com.ffusion.beans.fx.FXRateSheet;
import com.ffusion.fx.FXException;
import com.ffusion.services.fx.interfaces.ForeignExchangeService;
import com.ffusion.util.beans.DateTime;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Locale;

public class ForeignExchangeServiceMockImpl implements ForeignExchangeService {
    @Override
    public FXRate getFXRate(int i, String s, String s1, HashMap hashMap) throws FXException {
        return new FXRate();
    }

    @Override
    public FXRate getFXRate(int i, String s, String s1, DateTime dateTime, HashMap hashMap) throws FXException {
        FXRate fxRate = new FXRate();
        fxRate.setBuyPrice(new Currency("1", s,new Locale("en-us"), s1));
        return fxRate;
    }

    @Override
    public FXRate getFXRate(int i, Connection connection, String s, String s1, DateTime dateTime, int i1, String s2, HashMap hashMap) throws FXException {
        FXRate fxRate = new FXRate();
        fxRate.setBuyPrice(new Currency("1", s,new Locale("en-us"), s1));
        return fxRate;
    }

    @Override
    public FXRate getFXRate(int i, String s, String s1, int i1, String s2, HashMap hashMap) throws FXException {
        FXRate fxRate = new FXRate();
        fxRate.setBuyPrice(new Currency("1", s,new Locale("en-us"), s1));
        return fxRate;
    }

    @Override
    public FXRate getFXRate(int i, String s, String s1, DateTime dateTime, int i1, String s2, HashMap hashMap) throws FXException {
        FXRate fxRate = new FXRate();
        fxRate.setBuyPrice(new Currency("1", s,new Locale("en-us"), s1));
        return fxRate;
    }

    @Override
    public FXRate getClosestFXRate(int i, String s, String s1, DateTime dateTime, int i1, String s2, HashMap hashMap) throws FXException {
        FXRate fxRate = new FXRate();
        fxRate.setBuyPrice(new Currency("1", s,new Locale("en-us"), s1));
        return fxRate;
    }

    @Override
    public FXRate getClosestFXRate(int i, Connection connection, String s, String s1, DateTime dateTime, int i1, String s2, HashMap hashMap) throws FXException {
        FXRate fxRate = new FXRate();
        fxRate.setBuyPrice(new Currency("1", s,new Locale("en-us"), s1));
        return fxRate;
    }

    @Override
    public FXRateSheet getFXRateSheet(int i, String s, HashMap hashMap) throws FXException {
        return new FXRateSheet();
    }

    @Override
    public FXRateSheet getFXRateSheet(int i, String s, int i1, String s1, HashMap hashMap) throws FXException {
        return new FXRateSheet();
    }

    @Override
    public FXRateSheet getFXRateSheet(int i, String s, DateTime dateTime, int i1, String s1, HashMap hashMap) throws FXException {
        return new FXRateSheet();
    }

    @Override
    public FXRateSheet getFXRateSheetForTarget(int i, String s, int i1, String s1, HashMap hashMap) throws FXException {
        return new FXRateSheet();
    }

    @Override
    public FXCurrencies getCurrencies(int i, HashMap hashMap) throws FXException {
        return new FXCurrencies();
    }

    @Override
    public FXCurrencies getBaseCurrencies(int i, HashMap hashMap) throws FXException {
        return new FXCurrencies();
    }

    @Override
    public FXCurrencies getBaseCurrenciesGivenTarget(int i, String s, HashMap hashMap) throws FXException {
        return new FXCurrencies();
    }

    @Override
    public FXCurrencies getTargetCurrencies(int i, HashMap hashMap) throws FXException {
        return new FXCurrencies();
    }

    @Override
    public void cleanup(int i, HashMap hashMap) throws FXException {

    }
}
