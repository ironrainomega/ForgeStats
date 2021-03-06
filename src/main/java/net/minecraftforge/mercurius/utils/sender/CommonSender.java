package net.minecraftforge.mercurius.utils.sender;

import com.google.gson.Gson;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.mercurius.StatsMod;
import net.minecraftforge.mercurius.dataModels.StatsPingModel;
import net.minecraftforge.mercurius.dataModels.StatsStartModel;
import net.minecraftforge.mercurius.events.StatsCollectionEvent;
import net.minecraftforge.mercurius.helpers.StatsConstants;
import net.minecraftforge.mercurius.utils.Commands;
import net.minecraftforge.mercurius.utils.GameEnvironment;
import net.minecraftforge.mercurius.utils.LogHelper;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

/**
 * Created by tamas on 7/15/16.
 */
public abstract class CommonSender {

    public StatsPingModel data;

    public abstract boolean IsSnooperDisabled();

    public String toJSON(StatsPingModel model) {
        Gson json = new Gson();

        return json.toJson(model);
    }

    public void CollectData() throws Exception {
        this.CollectData(true);
    }

    public void CollectData(Commands cmd) throws Exception {
        this.CollectData(cmd, true);
    }

    public void CollectData(boolean upload) throws Exception {
        this.CollectData(Commands.PING, upload);
    }

    public void CollectData(Commands cmd, boolean upload) throws Exception {

        if (this.IsSnooperDisabled()) {
            LogHelper.info("Snooper is disabled... aborting collection.");
            return;
        }

        LogHelper.info("Starting collecting data for event "+cmd.toString());

        StatsPingModel model = new StatsPingModel();
        model.cmd = cmd;
        model.InstallID = StatsConstants.InstallID;
        model.SessionID = StatsConstants.SessionID;

        MinecraftForge.EVENT_BUS.post(new StatsCollectionEvent(cmd));
        model.Mods = StatsCollectionEvent.modProvidedData;

        if (cmd == Commands.START) {
            model = new StatsStartModel(model);

            ((StatsStartModel)model).ClientDateTimeEpoch = System.currentTimeMillis() / 1000L;
            ((StatsStartModel)model).JavaVersion = System.getProperty("java.version");
            ((StatsStartModel)model).JavaAllocatedRAM = Runtime.getRuntime().totalMemory();
            ((StatsStartModel)model).JavaMaxRAM = Runtime.getRuntime().maxMemory();
            ((StatsStartModel)model).MinecraftVersion = net.minecraftforge.fml.common.Loader.instance().getMCVersionString();
            ((StatsStartModel)model).modPack = StatsConstants.modPack;
            ((StatsStartModel)model).Environment = GameEnvironment.getEnvironment();

            this.addAllModData(((StatsStartModel)model));
        }

        this.data = optOutDataFromModel(model);
        if (upload) {
            this.Upload();
        }
    }

    public void Upload() throws Exception {
        String json = this.toJSON(this.data);
        LogHelper.info(json);
        Upload(json);
    }

    public void Upload(final String json) throws Exception {
        Thread newThread = new Thread() {
            public void run() {
                String ret = CommonSender.post(json);
                LogHelper.info(ret);
            }
        };
        newThread.setName("ForgeStatsThread");
        newThread.start();

    }

    private static String post(String json)
    {
        try {
            String data = "stat=" +  URLEncoder.encode(json, "UTF-8");

            HttpURLConnection conn = (HttpURLConnection)(new URL(StatsConstants.forgeServerUrl)).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Content-Length", "" + data.getBytes().length);
            conn.setRequestProperty("Content-Language", "en-US");
            conn.setUseCaches(false);
            conn.setDoOutput(true);
            DataOutputStream out = new DataOutputStream(conn.getOutputStream());
            out.writeBytes(data);
            out.flush();
            out.close();
            BufferedReader in_ = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuffer ret = new StringBuffer();
            String line;

            while ((line = in_.readLine()) != null)
            {
                ret.append(line);
                ret.append('\r');
            }

            in_.close();
            return ret.toString();
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return e.toString();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return e.toString();
        }
    }

    private static Timer timer = new Timer();

    public void StartTimer() {
        LogHelper.info("Starting timer...");
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                try {
                    StatsMod.sender.CollectData();
                } catch (Exception e) {
                    //
                    e.printStackTrace();
                }

            }
        }, StatsConstants.KEEPALIVETIME, StatsConstants.KEEPALIVETIME);

    }

    public void CancelTimer() {
        if (timer != null) {
            timer.cancel();
        }
    }

    private StatsPingModel optOutDataFromModel(StatsPingModel model) {
        if (StatsConstants.dataConfig.get(Configuration.CATEGORY_GENERAL, "InstallID_OptOut", false).getBoolean()) {
            model.InstallID = "";
        }

        if (StatsConstants.dataConfig.get(Configuration.CATEGORY_GENERAL, "SessionID_OptOut", false).getBoolean()) {
            model.SessionID = "";
        }

        if (model instanceof StatsStartModel) {
            if (StatsConstants.dataConfig.get(Configuration.CATEGORY_GENERAL, "ClientDateTimeEpoch_OptOut", false).getBoolean()) {
                ((StatsStartModel)model).ClientDateTimeEpoch = 0;
            }

            if (StatsConstants.dataConfig.get(Configuration.CATEGORY_GENERAL, "Environment_OptOut", false).getBoolean()) {
                ((StatsStartModel)model).Environment = GameEnvironment.OPTED_OUT;
            }

            if (StatsConstants.dataConfig.get(Configuration.CATEGORY_GENERAL, "JavaAllocatedRAM_OptOut", false).getBoolean()) {
                ((StatsStartModel)model).JavaAllocatedRAM = 0;
            }

            if (StatsConstants.dataConfig.get(Configuration.CATEGORY_GENERAL, "JavaMaxRAM_OptOut", false).getBoolean()) {
                ((StatsStartModel)model).JavaMaxRAM = 0;
            }

            if (StatsConstants.dataConfig.get(Configuration.CATEGORY_GENERAL, "JavaVersion_OptOut", false).getBoolean()) {
                ((StatsStartModel)model).JavaVersion = "";
            }

            if (StatsConstants.dataConfig.get(Configuration.CATEGORY_GENERAL, "MinecraftVersion_OptOut", false).getBoolean()) {
                ((StatsStartModel)model).MinecraftVersion = "";
            }

            if (StatsConstants.dataConfig.get(Configuration.CATEGORY_GENERAL, "modPack_OptOut", false).getBoolean()) {
                ((StatsStartModel)model).modPack = "";
            }
        }

        @SuppressWarnings("unchecked")
        Hashtable<String, Hashtable<String, Object>> cloneMods = (Hashtable<String, Hashtable<String, Object>>)model.Mods.clone();

        for(String key : cloneMods.keySet()) {
            if (StatsConstants.dataConfig.get(Configuration.CATEGORY_GENERAL, key + "_OptOut", false).getBoolean()) {
                model.Mods.remove(key);
            }
        }

        return model;
    }

    private void addAllModData(StatsStartModel model) {
        for (ModContainer mod : Loader.instance().getModList()) {
            Hashtable<String, Object> modData =  model.Mods.get(mod.getModId());
            boolean newEntry = false;
            if (modData == null) {
                modData = new Hashtable<String, Object>();
                newEntry = true;
            }

            modData.put("Version", mod.getVersion());
            modData.put("Enabled", Loader.instance().getActiveModList().contains(mod));

            if (newEntry) {
                model.Mods.put(mod.getModId(), modData);
            }
        }
    }
}
