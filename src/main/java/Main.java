import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class Main {
    public static void main(String[] args) {
        String downloadSetupURL = "https://yaeye.yadreno.com/f/YaEyeBot_Setup.exe";
        String downloadCheckURL = "https://yaeye.yadreno.com/f/YaEyeBotCheckDataOnline.txt";

        try {
            //перенаправляем вывод в файл
            System.setErr(new PrintStream(new FileOutputStream("err.log")));
            System.setOut(new PrintStream(new FileOutputStream("out.log")));

            System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Читаем сохраненные проверочные данные\n");
            //чтение проверочного файла ради получения местного пути и списка важных файлов
            BufferedReader reader = new BufferedReader(new FileReader("YaEyeBotCheckData.txt"));
            String pathToYaEyeBot = reader.readLine();
            reader.readLine();
            ArrayList<String> vipFilesYaEyeBot = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null)
                vipFilesYaEyeBot.add(line);
            reader.close();
            //скачивание и чтение проверочного файла ради сумм
            URI uriCheck = new URI(downloadCheckURL);
            String fnCheckData = "YaEyeBotCheckDataOnline.txt";
            Files.copy(uriCheck.toURL().openStream(), Paths.get(fnCheckData), REPLACE_EXISTING);
            reader = new BufferedReader(new FileReader(fnCheckData));
            reader.readLine();
            String checkSumYaEyeBot = reader.readLine();
            reader.close();

            if (pathToYaEyeBot != null) {
                String botNeedUpdate;
                System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Получаем текущие проверочные данные\n");
                //получаем контрольные суммы и прочие контрольные данные
                BotCheckData botCheckData = new BotCheckData(new File(pathToYaEyeBot));
                System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Было/стало: контрольная сумма (" + checkSumYaEyeBot + "/" + botCheckData.checkSum +
                        "), важных файлов (" + vipFilesYaEyeBot.toArray().length + "/" + botCheckData.vipFiles.toArray().length + ")\n");
                System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Старые файлы: " + vipFilesYaEyeBot + "\n");
                System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Новые файлы: " + botCheckData.vipFiles + "\n");

                try {
                    reader = new BufferedReader(new FileReader(pathToYaEyeBot + "\\BotNeedUpdate.txt"));
                    botNeedUpdate = reader.readLine();
                    reader.close();

                    System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Наличие сигнала с прошлой сессии об обновлении: " + botNeedUpdate + "\n");
                    //проверяем, что все важные файлы настроек находятся на месте
                    //и контрпольные суммы статичной части бота не изменились,
                    //иначе - накатываем обновление полностью
                    if (
                            !(vipFilesYaEyeBot.containsAll(botCheckData.vipFiles) && botCheckData.vipFiles.containsAll(vipFilesYaEyeBot)) ||
                                    !Objects.equals(checkSumYaEyeBot, Long.toString(botCheckData.checkSum))
                    )
                        throw new FileNotFoundException();
                } catch (FileNotFoundException e) {
                    botNeedUpdate = "1";
                }
                System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Итоговое решение об обновлении: " + botNeedUpdate + "\n");

                //восстанавливаем из резервных копий настройки бота - всегда, т.к. они могли быть затерты злоумышленником
                copyFileToDir("settings.json", pathToYaEyeBot);
                copyFileToDir("MTSUpdate.ps1", pathToYaEyeBot);
                //сбрасываем флаг начала записи видео
                FileWriter fwWriteVideo = new FileWriter(pathToYaEyeBot + "\\WriteVideo.txt");
                fwWriteVideo.write("0");
                fwWriteVideo.close();
                //сбрасываем флаг необходимости обновления
                FileWriter fwBotNeedUpdate = new FileWriter(pathToYaEyeBot + "\\BotNeedUpdate.txt");
                fwBotNeedUpdate.write("0");
                fwBotNeedUpdate.close();

                //обновляем бота, если была поставлена метка о необходимости обновления, или нарушена целостность бота
                if (botNeedUpdate != null && botNeedUpdate.trim().equals("1") && autoUpdate()) {
                    System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Запускаем обновление\n");
                    //скачивание дистрибутива
                    URI uriSetup = new URI(downloadSetupURL);
                    String fnSetup = "YaEyeBot_Setup.exe";
                    Files.copy(uriSetup.toURL().openStream(), Paths.get(fnSetup), REPLACE_EXISTING);
                    //запуск дистрибутива
                    List<String> command = new ArrayList<>();
                    command.add(fnSetup);
                    command.add("/VERYSILENT");
                    ProcessBuilder processBuilder = new ProcessBuilder(command);
                    processBuilder.redirectErrorStream(true);
                    processBuilder.start();
                } else {
                    System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Просто запускаем бота\n");
                    //запуск бота
                    exec(pathToYaEyeBot, "YaEyeBot.jar");
                }
            }
        } catch (Exception e) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            e.printStackTrace();
        }
    }

    //чтение настроек
    public static boolean autoUpdate() {
        JSONParser parser = new JSONParser();
        FileReader fr;

        try {
            fr = new FileReader("settings.json");
            Object obj = parser.parse(fr);
            fr.close();

            JSONObject jsonObject = (JSONObject) obj;

            boolean res = (jsonObject.get("autoUpdate") != null) &&
                    Objects.equals(jsonObject.get("autoUpdate").toString(), "1");
            System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Обновление включено в настройках: " + res + "\n");
            return res;
        } catch (Exception err) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            err.printStackTrace();
        }
        return false;
    }

    public static void exec(String path, String filename) throws IOException {
        String javaBin = System.getProperty("java.home") + "\\bin\\java";

        ProcessBuilder pb = new ProcessBuilder(javaBin, "-jar", path + "\\" + filename);
        pb.directory(new File(path));
        pb.start();
    }

    public static void copyFileToDir(String sourceFileName, String destinationDirectory) {
        File sourceFile = new File(sourceFileName);
        File destinationFile = new File(destinationDirectory + "\\" + sourceFile.getName());

        try {
            File destinationFolder = new File(destinationDirectory);
            if (!destinationFolder.exists())
                if (!destinationFolder.mkdirs())
                    throw new Exception("Не удалось создать " + destinationDirectory);

            Files.copy(sourceFile.toPath(), destinationFile.toPath(), REPLACE_EXISTING);
        } catch (Exception e) {
            System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Error copying file: " + e.getMessage() + "\n");
        }
    }
}