/*
 * <<
 *  Davinci
 *  ==
 *  Copyright (C) 2016 - 2019 EDP
 *  ==
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *        http://www.apache.org/licenses/LICENSE-2.0
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  >>
 *
 */

package edp.davinci.service.screenshot;

import com.alibaba.druid.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

import static edp.davinci.service.screenshot.BrowserEnum.valueOf;

@Slf4j
@Component
public class ScreenshotUtil {

    @Value("${screenhot.default_browser:CHROME}")
    private String DEFAULT_BROWSER;

    @Value("${screenhot.chromedriver_path:}")
    private String CHROME_DRIVER_PATH;

    @Value("${screenhot.phantomjs_path:}")
    private String PHANTOMJS_PATH;


    private static final int DEFAULT_SCREENSHOT_WIDTH = 1920;
    private static final int DEFAULT_SCREENSHOT_HEIGHT = 1080;


    public void screenshot(List<ImageContent> imageContents) {
        ExecutorService executorService = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch countDownLatch = new CountDownLatch(imageContents.size());
            List<Future> futures = new ArrayList<>(imageContents.size());
            imageContents.forEach(content -> futures.add(executorService.submit(() -> {
                try {
                    File image = doScreenshot(content.getUrl());
                    content.setContent(image);
                } catch (Exception e) {
                    log.error("error ScreenshotUtil.screenshot, ", e);
                    e.printStackTrace();
                } finally {
                    countDownLatch.countDown();
                }
            })));

            try {
                for (Future future : futures) {
                    future.get();
                }
                countDownLatch.await();
            } catch (ExecutionException e) {
                executorService.shutdownNow();
            }

            imageContents.sort(Comparator.comparing(ImageContent::getOrder));

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            executorService.shutdown();
        }
    }


    private File doScreenshot(String url) throws Exception {
        WebDriver driver = generateWebDriver();
        driver.get(url);

        System.out.println("getting... " + url);
        try {
            WebDriverWait wait = new WebDriverWait(driver, 60000);

            ExpectedCondition<WebElement> ConditionOfSign = ExpectedConditions.presenceOfElementLocated(By.id("headlessBrowserRenderSign"));
            ExpectedCondition<WebElement> ConditionOfWidth = ExpectedConditions.presenceOfElementLocated(By.id("width"));
            ExpectedCondition<WebElement> ConditionOfHeight = ExpectedConditions.presenceOfElementLocated(By.id("height"));

            wait.until(ExpectedConditions.or(ConditionOfSign, ConditionOfWidth, ConditionOfHeight));

            String widthVal = driver.findElement(By.id("width")).getAttribute("value");
            String heightVal = driver.findElement(By.id("height")).getAttribute("value");

            int width = DEFAULT_SCREENSHOT_WIDTH;
            int height = DEFAULT_SCREENSHOT_HEIGHT;

            if (!StringUtils.isEmpty(widthVal)) {
                width = Integer.parseInt(widthVal);
            }

            if (!StringUtils.isEmpty(heightVal)) {
                height = Integer.parseInt(heightVal);
            }
            driver.manage().window().setSize(new Dimension(width, height));
            Thread.sleep(2000);
            return ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
        return null;
    }

    private WebDriver generateWebDriver() throws ExecutionException {
        WebDriver driver;
        BrowserEnum browserEnum = valueOf(DEFAULT_BROWSER);
        switch (browserEnum) {
            case CHROME:
                driver = generateChromeDriver();
                break;
            case PHANTOMJS:
                driver = generatePhantomJsDriver();
                break;
            default:
                throw new IllegalArgumentException("Unknown Web browser :" + DEFAULT_BROWSER);
        }

        driver.manage().timeouts().implicitlyWait(3, TimeUnit.MINUTES);
        driver.manage().window().maximize();
        driver.manage().window().setSize(new Dimension(DEFAULT_SCREENSHOT_WIDTH, DEFAULT_SCREENSHOT_HEIGHT));
        return driver;
    }


    private WebDriver generateChromeDriver() throws ExecutionException {
        if (!new File(CHROME_DRIVER_PATH).setExecutable(true)) {
            throw new ExecutionException(new Exception(CHROME_DRIVER_PATH + "is not executable!"));
        }

        log.info("Generating Chrome driver ({})...", CHROME_DRIVER_PATH);
        System.setProperty(ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY, CHROME_DRIVER_PATH);
        ChromeOptions options = new ChromeOptions();

        options.addArguments("headless");
        options.addArguments("no-sandbox");
        options.addArguments("disable-gpu");
        options.addArguments("disable-features=NetworkService");
        options.addArguments("ignore-certificate-errors");
        options.addArguments("silent");
        options.addArguments("--disable-application-cache");

        return new ChromeDriver(options);
    }

    private WebDriver generatePhantomJsDriver() throws ExecutionException {
        if (!new File(PHANTOMJS_PATH).setExecutable(true)) {
            throw new ExecutionException(new Exception(PHANTOMJS_PATH + "is not executable!"));
        }
        log.info("Generating PhantomJs driver ({})...", PHANTOMJS_PATH);
        System.setProperty(PhantomJSDriverService.PHANTOMJS_EXECUTABLE_PATH_PROPERTY, PHANTOMJS_PATH);

        return new PhantomJSDriver();
    }
}