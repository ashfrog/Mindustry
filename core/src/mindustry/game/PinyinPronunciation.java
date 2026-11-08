package mindustry.game;

import arc.util.*;
import java.nio.charset.*;
import java.util.*;

/** Speak only the current learning token (Hanzi or pinyin), replacing stale speech. */
public class PinyinPronunciation{
    private Process process;
    private long revision;

    public synchronized void stop(){
        revision++;
        if(process != null){ process.destroy(); process = null; }
    }

    public void speak(String character){
        if(!OS.isWindows) return;
        final long request;
        synchronized(this){ stop(); request = revision; }
        Thread worker = new Thread(() -> {
            synchronized(this){
                if(request != revision) return;
                try{
                    process = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encodedCommand(character)).start();
                }catch(Exception error){
                    Log.warn("Unable to speak pinyin question: @", error.getMessage());
                }
            }
        }, "pinyin-voice");
        worker.setDaemon(true);
        worker.start();
    }

    public static String encodedCommand(String character){
        String text = character.replace("'", "''");
        String script = "Add-Type -AssemblyName System.Speech; "
            + "$voice=New-Object System.Speech.Synthesis.SpeechSynthesizer; "
            + "try { $voice.SelectVoice('Microsoft Huihui Desktop') } catch { $voice.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::NotSet,[System.Speech.Synthesis.VoiceAge]::NotSet,0,[System.Globalization.CultureInfo]::GetCultureInfo('zh-CN')) }; "
            + "try { $voice.Rate=-1; $voice.Speak('" + text + "') } finally { $voice.Dispose() };";
        return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    }
}
