import org.apache.commons.cli.*;
import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;

/**
 * 用于处理BlazeMeter录制的jmx文件
 * <p>
 * 主要功能:
 * 1. 清除跨域产生的OPTIONS请求
 * <p>
 * Created by surpass.wei@gmail.com on 2021/5/27.
 */
public class App {
    public static void main(String[] args) throws DocumentException, IOException {
        Option paramH = new Option("h", "help", false, "帮助文档");
        Option paramI = new Option("i", "input", true, "需要处理的jmx文件路径");
        Option paramO = new Option("o", "output", true, "处理完毕的jmx文件生成路径");

        Options options = new Options();
        options.addOption(paramH);
        options.addOption(paramI);
        options.addOption(paramO);

        CommandLine cli;
        CommandLineParser cliParser = new DefaultParser();
        HelpFormatter helpFormatter = new HelpFormatter();

        try {
            cli = cliParser.parse(options, args);
        } catch (ParseException e) {
            // 解析失败是用 HelpFormatter 打印 帮助信息
            helpFormatter.printHelp("使用帮助", options);
            e.printStackTrace();
            return;
        }

        if (cli.hasOption("h")) {
            helpFormatter.printHelp("使用帮助", options);
            return;
        }

        new App().handler(cli.getOptionValue("i"), cli.getOptionValue("o"));
    }

    /**
     * jmx文件处理
     *
     * @param inputPath  文件输入路径
     * @param outputPath 文件输出路径
     */
    private void handler(String inputPath, String outputPath) throws IOException, DocumentException {
        if (inputPath == null || outputPath == null) {
            throw new IllegalArgumentException("错误的路径参数");
        }

        //  创建一个XML解析器对象
        SAXReader reader = new SAXReader();

        //  读取XML文档，返回Document对象
        Document document = reader.read(new File(inputPath));

        //  清除OPTIONS请求
        List<Node> list = document.selectNodes("//HTTPSamplerProxy");
        for (Node element : list) {
            Node node = element.selectSingleNode("stringProp[@name='HTTPSampler.method']");
            if ("OPTIONS".equals(node.getText())) {
                Element parent = element.getParent();
                Node hashTree = element.selectSingleNode("following-sibling::hashTree[1]");
                parent.remove(element);
                parent.remove(hashTree);
            }
        }

        //  处理hashTree子节点中collectionProp附加了reference的情况
        List<Node> allHashTree = document.selectNodes("//HTTPSamplerProxy/following-sibling::hashTree");
        int i = 1;
        for (Node node : allHashTree) {
            Element collectionProp = (Element) node.selectSingleNode(".//collectionProp");
            Attribute reference = collectionProp.attribute("reference");
            if (reference != null) {
                if (i == 1) {
                    reference.setValue("../../../HTTPSamplerProxy/elementProp[2]/collectionProp");
                } else {
                    reference.setValue("../../../HTTPSamplerProxy[" + i + "]/elementProp[2]/collectionProp");
                }
            }
            i++;
        }

        OutputFormat format = OutputFormat.createPrettyPrint();
        XMLWriter writer = new XMLWriter(
                new OutputStreamWriter(new FileOutputStream(outputPath)), format);

        //  忽略 Element 对象中的转义字符
        writer.setEscapeText(false);
        writer.write(document);
        writer.close();
    }
}
