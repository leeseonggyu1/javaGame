import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.stb.STBImage;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.system.MemoryUtil.*;


class Enemy {
    float x, y, z;          // 위치
    boolean isAlive;        // 살아있는 상태
    long spawnTime;         // 생성 시간
    float[] color;          // 적의 색상 (RGB 배열)
    long lifetime;          // 적이 유지될 시간 (밀리초 단위)
    float speedX;           // X 방향 속도
    float speedY;           // Y 방향 속도

    // 생성자
    Enemy(float x, float y, float z, float[] color, float speedX, float speedY) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color; // RGB 색상
        this.lifetime = 3000; // 모든 표적의 생존 시간은 3초
        this.isAlive = true;
        this.spawnTime = System.currentTimeMillis(); // 생성 시간 기록
        this.speedX = speedX; // X 방향 속도
        this.speedY = speedY; // Y 방향 속도
    }

    // 표적 이동 메서드
    void move() {
        this.x += speedX;
        this.y += speedY;
    }
}



public class ShootingGame {
	private int gameState = 0; // 0: 메인 메뉴, 1: 게임 플레이, 2: 게임 종료, 3: 카운트다운
	private SoundPlayer soundPlayer; // SoundPlayer 인스턴스
	private long lastSoundTime = 0; // 마지막 사운드 재생 시간
    public ShootingGame() {
        initializeRankingBoard(); // 랭킹 보드 초기화
    }


    private long window;
    private ArrayList<Enemy> enemies = new ArrayList<>();
    private Random random = new Random();
    private int score = 0;
    private float enemySpeed = 0.01f;

    private float cameraYaw = 0.0f;
    private float cameraPitch = 0.0f;
    private float playerX = 0.0f;
    private float playerZ = 0.0f;
    private float moveSpeed = 0.1f;
    private float cameraHeight = 2.0f;

    private long lastClickTime = 0;
    
    private SoundPlayer shootingSound;
    private SoundPlayer targetHitSound;

    private ByteBuffer fontData;
    private STBTTFontinfo fontInfo;
    private float fontScale;
    private int fontAscent;
    private static final int GAME_DURATION = 120_000; // 게임 제한시간 120초 (밀리초)
    private long gameStartTime; // 게임 시작 시간
    private boolean gameOver = false; // 게임 종료 여부
    private boolean isCountdownInitialized = false; // 카운트다운 초기화 여부
    
    private int ammo = 30; // 현재 탄창에 남은 총알 수
    private final int maxAmmo = 30; // 탄창 용량
    private boolean isReloading = false; // 재장전 상태
    
    private float recoilPitch = 0.0f; // 수직 반동
    private float recoilYaw = 0.0f;   // 수평 반동
    private float recoilRecoverySpeed = 0.05f; // 복구 속도
    private float spawnTimer = 0.0f;
    private long lastEnterPressTime = 0; // 마지막 엔터 키 입력 시간

    


    



    public void run() {
        init();
        gameStartTime = System.currentTimeMillis(); // 게임 시작 시간 설정
        loop();
        cleanup();
    }
    
    public class SoundPlayer {
        public void playSound(String filePath) {
            try {
                // .wav 파일 전체 경로 지정
                File soundFile = new File(filePath);
                if (!soundFile.exists()) {
                    throw new IOException("Sound file not found: " + soundFile.getAbsolutePath());
                }

                // 새로운 AudioInputStream과 Clip 생성
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundFile);
                Clip clip = AudioSystem.getClip();
                clip.open(audioStream);

                // 재생 시작
                clip.start();

                // 재생이 끝나면 클립 닫기
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });

            } catch (UnsupportedAudioFileException e) {
                System.err.println("Unsupported audio file: " + filePath);
                e.printStackTrace();
            } catch (IOException e) {
                System.err.println("Error loading audio file: " + filePath);
                e.printStackTrace();
            } catch (LineUnavailableException e) {
                System.err.println("Audio line unavailable: " + filePath);
                e.printStackTrace();
            }
        }
    }
    
    private String[][] rankingBoard = new String[10][2]; // 최대 10명의 랭킹 저장
    private int playerScore = 0; // 현재 플레이어 점수
    private String playerName = "Player"; // 현재 플레이어 이름

    private void initializeRankingBoard() {
        for (int i = 0; i < rankingBoard.length; i++) {
            rankingBoard[i] = new String[] {"", "0"}; // 모든 항목을 빈 문자열과 0으로 초기화
        }

        // 기본 데이터 추가 (예시)
        rankingBoard[0] = new String[] {"Alice", "90"};
        rankingBoard[1] = new String[] {"Bob", "80"};
        rankingBoard[2] = new String[] {"Charlie", "70"};
        rankingBoard[3] = new String[] {"David", "60"};
        rankingBoard[4] = new String[] {"Eve", "50"};
    }


    private void updateRankingBoard(String username, int score) {
        // 새로운 점수 추가
        for (int i = 0; i < rankingBoard.length; i++) {
            if (rankingBoard[i][0] == null || rankingBoard[i][0].isEmpty()) {
                rankingBoard[i] = new String[] {username, String.valueOf(score)};
                break;
            }
        }

        // 점수를 기준으로 내림차순 정렬
        Arrays.sort(rankingBoard, (a, b) -> {
            int scoreA = (a[1] != null && !a[1].isEmpty()) ? Integer.parseInt(a[1]) : -1;
            int scoreB = (b[1] != null && !b[1].isEmpty()) ? Integer.parseInt(b[1]) : -1;
            return scoreB - scoreA; // 내림차순 정렬
        });
    }


    private int getPlayerRank(int score) {
        for (int i = 0; i < rankingBoard.length; i++) {
            if (rankingBoard[i][1] != null && !rankingBoard[i][1].isEmpty() &&
                Integer.parseInt(rankingBoard[i][1]) == score) {
                return i + 1; // 배열 인덱스는 0부터 시작하므로 1을 더함
            }
        }
        return -1; // 순위 없음
    }


    
    
    public class TextureLoader {
        public static int loadTexture(String path) throws Exception {
            int textureID;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                IntBuffer channels = stack.mallocInt(1);

                ByteBuffer image = STBImage.stbi_load(path, width, height, channels, 4); // RGBA
                if (image == null) {
                    throw new RuntimeException("Failed to load texture file: " + path);
                }

                textureID = glGenTextures();
                glBindTexture(GL_TEXTURE_2D, textureID);

                // 텍스처 매핑 설정
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

                // 텍스처 데이터 업로드
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width.get(0), height.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
                STBImage.stbi_image_free(image);
            }
            return textureID;
        }
    }

    private int floorTexture, wallTexture, frontWallTexture, mainMenuBackground;
    private int redTargetTexture, blueTargetTexture, yellowTargetTexture;
    private int gunTexture;

    
    
    private void loadTextures() {
        try {
        	
            floorTexture = TextureLoader.loadTexture("src/textures/block/metalwall.png");
            wallTexture = TextureLoader.loadTexture("src/textures/block/brickwall.png");
            frontWallTexture = TextureLoader.loadTexture("src/textures/block/metalwall.png");
            mainMenuBackground = TextureLoader.loadTexture("src/textures/block/main.png");
            gunTexture = TextureLoader.loadTexture("src/textures/block/gun.png");
            loadSkyTexture("src/textures/block/DaySkyHDRI015A_4K-TONEMAPPED.png");

            // 타겟 텍스처 로드
            redTargetTexture = TextureLoader.loadTexture("src/textures/block/redtarget.png");
            blueTargetTexture = TextureLoader.loadTexture("src/textures/block/bluetarget.png");
            yellowTargetTexture = TextureLoader.loadTexture("src/textures/block/yellowtarget.png");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to load textures!");
        }
    }






    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(1920, 1080, "3D Shooting Game", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();
        soundPlayer = new SoundPlayer();

        glEnable(GL_DEPTH_TEST);
        glClearColor(0.5f, 0.7f, 1.0f, 1.0f); // 하늘색 배경

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float aspectRatio = 1920f / 1080f;
        glFrustum(-aspectRatio, aspectRatio, -1.0, 1.0, 2.0, 1000.0); // zNear = 2.0
        glMatrixMode(GL_MODELVIEW);

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        // Load font
        loadFont("C:\\java_workspace\\HelloLWJGLProject\\bin\\fonts\\Arial.ttf", 48); // 폰트 경로와 크기


        System.out.println("Sounds initialized successfully!");
    }

    

    private void renderGunIcon(int x, int y, int width, int height) {
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, gunTexture);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, 1920, 1080, 0, -1, 1); // 화면 좌표 설정
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(x, y); // 좌측 상단
        glTexCoord2f(1, 0); glVertex2f(x + width, y); // 우측 상단
        glTexCoord2f(1, 1); glVertex2f(x + width, y + height); // 우측 하단
        glTexCoord2f(0, 1); glVertex2f(x, y + height); // 좌측 하단
        glEnd();

        glDisable(GL_BLEND);
        glBindTexture(GL_TEXTURE_2D, 0);
        glDisable(GL_TEXTURE_2D);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }


    private void renderAmmo(int ammo, int maxAmmo, int x, int y) {
        String ammoText = ammo + "/" + maxAmmo;

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, 1920, 1080, 0, -1, 1); // 화면 좌표 설정
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor3f(1.0f, 1.0f, 1.0f); // 기본 흰색 설정

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xBuffer = stack.floats(x);

            for (char c : ammoText.toCharArray()) {
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                IntBuffer xOffset = stack.mallocInt(1);
                IntBuffer yOffset = stack.mallocInt(1);

                ByteBuffer bitmap = STBTruetype.stbtt_GetCodepointBitmap(
                    fontInfo, 0, fontScale, c, width, height, xOffset, yOffset
                );

                if (bitmap == null || width.get(0) <= 0 || height.get(0) <= 0) {
                    continue; // 비트맵 데이터가 없으면 건너뜁니다.
                }

                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

                int texID = glGenTextures();
                glBindTexture(GL_TEXTURE_2D, texID);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, width.get(0), height.get(0), 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);

                glEnable(GL_TEXTURE_2D);
                glBegin(GL_QUADS);
                glTexCoord2f(0, 0); glVertex2f(xBuffer.get(0), y);
                glTexCoord2f(1, 0); glVertex2f(xBuffer.get(0) + width.get(0), y);
                glTexCoord2f(1, 1); glVertex2f(xBuffer.get(0) + width.get(0), y + height.get(0));
                glTexCoord2f(0, 1); glVertex2f(xBuffer.get(0), y + height.get(0));
                glEnd();

                glDisable(GL_TEXTURE_2D);
                glDeleteTextures(texID);

                // 다음 문자를 렌더링하기 위해 x 좌표 이동
                xBuffer.put(0, xBuffer.get(0) + width.get(0) + 2);
                STBTruetype.stbtt_FreeBitmap(bitmap);
            }
        }

        glDisable(GL_BLEND);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }




    private void loadFont(String fontPath, int fontSize) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            byte[] fontBytes = Files.readAllBytes(Path.of(fontPath));
            fontData = ByteBuffer.allocateDirect(fontBytes.length).put(fontBytes);
            fontData.flip();

            fontInfo = STBTTFontinfo.create();
            if (!STBTruetype.stbtt_InitFont(fontInfo, fontData)) {
                throw new IllegalStateException("Failed to initialize font.");
            }

            fontScale = STBTruetype.stbtt_ScaleForPixelHeight(fontInfo, fontSize);
            IntBuffer ascent = stack.mallocInt(1);
            STBTruetype.stbtt_GetFontVMetrics(fontInfo, ascent, null, null);
            fontAscent = (int) (ascent.get(0) * fontScale);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void renderText(String text, int x, int y) {
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, 1920, 1080, 0, -1, 1); // 화면 좌표 설정
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor3f(1.0f, 1.0f, 1.0f); // 텍스트 색상

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xBuffer = stack.floats(x);

            for (char c : text.toCharArray()) {
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                IntBuffer xOffset = stack.mallocInt(1);
                IntBuffer yOffset = stack.mallocInt(1);

                ByteBuffer bitmap = STBTruetype.stbtt_GetCodepointBitmap(
                    fontInfo, 0, fontScale, c, width, height, xOffset, yOffset
                );

                if (bitmap == null || width.get(0) <= 0 || height.get(0) <= 0) {
                    continue; // 비트맵 데이터가 없으면 건너뜁니다.
                }

                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

                int texID = glGenTextures();
                glBindTexture(GL_TEXTURE_2D, texID);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, width.get(0), height.get(0), 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);

                glEnable(GL_TEXTURE_2D);
                glBegin(GL_QUADS);
                glTexCoord2f(0, 0); glVertex2f(xBuffer.get(0), y);
                glTexCoord2f(1, 0); glVertex2f(xBuffer.get(0) + width.get(0), y);
                glTexCoord2f(1, 1); glVertex2f(xBuffer.get(0) + width.get(0), y + height.get(0));
                glTexCoord2f(0, 1); glVertex2f(xBuffer.get(0), y + height.get(0));
                glEnd();

                glDisable(GL_TEXTURE_2D);
                glDeleteTextures(texID);

                xBuffer.put(0, xBuffer.get(0) + width.get(0) + 2);
                STBTruetype.stbtt_FreeBitmap(bitmap);
            }
        }

        glDisable(GL_BLEND);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }
    private void resetCameraView() {
        cameraYaw = 0.0f;   // 좌우 회전 각도 초기화
        cameraPitch = 0.0f; // 상하 회전 각도 초기화
        playerX = 0.0f;     // 플레이어 초기 X 위치
        playerZ = 0.0f;     // 플레이어 초기 Z 위치
        cameraHeight = 2.0f; // 플레이어 시점 높이
    }


    private void loop() {
        loadTextures(); // 텍스처 로드

        float spawnTimer = 0;
        float spawnInterval = 0.8f;

        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            if (gameState == 0) { // 메인 메뉴 상태
                renderMainMenu();
                handleMainMenuInput();
            } else if (gameState == 3) { // 카운트다운 상태
                resetCameraView(); // 카운트다운 중 시점 유지
                renderCountdown(); // 카운트다운 렌더링
            } else if (gameState == 1) { // 게임 플레이 상태
                spawnTimer += 0.01f;
                if (spawnTimer >= spawnInterval) {
                    spawnEnemy();
                    spawnTimer = 0;
                    spawnInterval = Math.max(0.3f, spawnInterval - 0.01f);
                }

                // 플레이어 관련 업데이트
                updatePlayerPosition();
                updateCameraRotation();

                // 입력 처리
                checkMouseClick(); // 발사와 탄창 소모 처리
                checkReloadKey();  // 재장전 처리

                // 카메라 설정
                glLoadIdentity();
                glRotatef(cameraPitch + recoilPitch, 1.0f, 0.0f, 0.0f); // 수직 반동 적용
                glRotatef(cameraYaw + recoilYaw, 0.0f, 1.0f, 0.0f); // 수평 반동 적용
                applyRecoilRecovery();
                glTranslatef(-playerX, -cameraHeight, -playerZ);

            
                // 게임 오브젝트 렌더링
                renderFloor();
                renderWalls();
                renderEnemies();
                renderCrosshair();
                renderSkybox();

                // 재장전 중이라면 중앙에 텍스트 표시

                // UI 렌더링
                renderScore();
                renderTimer();
                renderAmmo(ammo, maxAmmo, 1500, 850); // 장탄수 표시
                renderGunIcon(1600, 750, 300, 300); // 총 모양 텍스처 표시

                // 게임 오버 체크
                checkGameOver();
                if (gameOver) {
                    gameState = 2; // 게임 종료 상태로 변경
                }
            } else if (gameState == 2) { // 게임 종료 상태
                renderGameOverScreen();
                handleGameOverInput();
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    
    private int skyTexture;

    private void loadSkyTexture(String path) {
        try {
            skyTexture = TextureLoader.loadTexture(path); // TextureLoader를 이용해 텍스처 로드
            System.out.println("Sky texture loaded successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to load sky texture!");
        }
    }
    private void renderSkybox() {
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, skyTexture);

        float size = 500.0f; // Skybox의 크기

        glBegin(GL_QUADS);

        // 앞면
        glTexCoord2f(0.0f, 0.0f); glVertex3f(-size, -size, -size); // 좌하단
        glTexCoord2f(1.0f, 0.0f); glVertex3f(size, -size, -size);  // 우하단
        glTexCoord2f(1.0f, 1.0f); glVertex3f(size, size, -size);   // 우상단
        glTexCoord2f(0.0f, 1.0f); glVertex3f(-size, size, -size);  // 좌상단

        // 오른쪽
        glTexCoord2f(0.0f, 0.0f); glVertex3f(size, -size, -size);
        glTexCoord2f(1.0f, 0.0f); glVertex3f(size, -size, size);
        glTexCoord2f(1.0f, 1.0f); glVertex3f(size, size, size);
        glTexCoord2f(0.0f, 1.0f); glVertex3f(size, size, -size);

        // 왼쪽
        glTexCoord2f(0.0f, 0.0f); glVertex3f(-size, -size, size);
        glTexCoord2f(1.0f, 0.0f); glVertex3f(-size, -size, -size);
        glTexCoord2f(1.0f, 1.0f); glVertex3f(-size, size, -size);
        glTexCoord2f(0.0f, 1.0f); glVertex3f(-size, size, size);

        // 위쪽
        glTexCoord2f(0.0f, 1.0f); glVertex3f(-size, size, -size);
        glTexCoord2f(0.0f, 0.0f); glVertex3f(size, size, -size);
        glTexCoord2f(1.0f, 0.0f); glVertex3f(size, size, size);
        glTexCoord2f(1.0f, 1.0f); glVertex3f(-size, size, size);

        // 아래쪽
        glTexCoord2f(0.0f, 0.0f); glVertex3f(-size, -size, -size);
        glTexCoord2f(1.0f, 0.0f); glVertex3f(size, -size, -size);
        glTexCoord2f(1.0f, 1.0f); glVertex3f(size, -size, size);
        glTexCoord2f(0.0f, 1.0f); glVertex3f(-size, -size, size);

        // 뒷면
        glTexCoord2f(1.0f, 0.0f); glVertex3f(-size, -size, size);
        glTexCoord2f(0.0f, 0.0f); glVertex3f(size, -size, size);
        glTexCoord2f(0.0f, 1.0f); glVertex3f(size, size, size);
        glTexCoord2f(1.0f, 1.0f); glVertex3f(-size, size, size);

        glEnd();

        glDisable(GL_TEXTURE_2D);
    }



    private void renderMainMenu() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // 화면 초기화

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, 1920, 1080, 0, -1, 1); // 화면의 해상도(1920x1080)에 맞게 좌표계를 설정

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glEnable(GL_TEXTURE_2D); // 텍스처 활성화
        glBindTexture(GL_TEXTURE_2D, mainMenuBackground); // 메인 메뉴 텍스처 바인딩

        // 화면 전체를 덮는 텍스처 그리기
        glBegin(GL_QUADS);
        glTexCoord2f(0.0f, 0.0f); glVertex2f(0.0f, 0.0f);          // 좌하단
        glTexCoord2f(1.0f, 0.0f); glVertex2f(1920.0f, 0.0f);       // 우하단
        glTexCoord2f(1.0f, 1.0f); glVertex2f(1920.0f, 1080.0f);    // 우상단
        glTexCoord2f(0.0f, 1.0f); glVertex2f(0.0f, 1080.0f);       // 좌상단
        glEnd();

        glDisable(GL_TEXTURE_2D); // 텍스처 비활성화

        // 텍스트 렌더링
        String startInstruction = "Press Enter to Start";
        renderText(startInstruction, 850, 900); // 텍스트를 화면 중앙에 표시

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }




    private long countdownStartTime; // 카운트다운 시작 시간
    
    private void handleMainMenuInput() {
        if (glfwGetKey(window, GLFW_KEY_ENTER) == GLFW_PRESS) { // 'ENTER' 키 확인
            gameState = 3;                // 카운트다운 상태로 전환
            gameOver = false;             // 게임 종료 상태 초기화
            score = 0;                    // 점수 초기화
            countdownStartTime = System.currentTimeMillis(); // 카운트다운 시작 시간 설정
            isCountdownInitialized = false; // 카운트다운 초기화 플래그 리셋
            resetGame();   // 게임 초기화
        }
    }





    private void handleGameOverInput() {
        long currentTime = System.currentTimeMillis();

        // 최소 입력 간격 설정 (200ms)
        if (currentTime - lastEnterPressTime >= 200 && glfwGetKey(window, GLFW_KEY_ENTER) == GLFW_PRESS) {
            lastEnterPressTime = currentTime; // 마지막 입력 시간 갱신
            gameState = 0; // 게임 상태를 메인 메뉴로 변경
        }
    }



    private void resetGame() {
        ammo = maxAmmo;
        recoilPitch = 0.0f;
        recoilYaw = 0.0f;
        isReloading = false;
        enemies.clear();
        spawnTimer = 0.0f;
        gameStartTime = System.currentTimeMillis();
    }


    
    private void renderCountdown() {
        if (!isCountdownInitialized) {
            resetCameraView();         // 시점 초기화
            isCountdownInitialized = true; // 초기화 완료 플래그 설정
        }

        long elapsedTime = System.currentTimeMillis() - countdownStartTime;
        int countdownValue = 3 - (int) (elapsedTime / 1000); // 남은 카운트 계산

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        String countdownText;
        if (countdownValue > 0) {
            countdownText = String.valueOf(countdownValue); // 3, 2, 1 표시
        } else {
            countdownText = "START!"; // 카운트다운 완료 후 "START!" 표시
        }

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, 1920, 1080, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // 카운트다운 텍스트 렌더링
        glColor3f(1.0f, 1.0f, 1.0f);
        renderText(countdownText, 900, 500); // 화면 중앙에 렌더링

        glDisable(GL_BLEND);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();

        // 카운트다운 완료 시 게임 시작
        if (elapsedTime > 4000) { // 3초 + 1초 ("START!" 표시 시간)
            gameState = 1;           // 게임 상태로 전환
            gameStartTime = System.currentTimeMillis(); // 게임 시작 시간 초기화
        }
    }




    
    private void checkGameOver() {
        long elapsedTime = System.currentTimeMillis() - gameStartTime;
        if (elapsedTime >= GAME_DURATION) {
            gameOver = true; // 게임 종료 상태 설정
            gameState = 2; // 게임 종료 상태로 변경

            // 랭킹 업데이트
            updateRankingBoard(playerName, playerScore);
        }
    }
    
    
    
    private void renderGameOverScreen() {
        // "Game Over" 메시지
        renderText("Game Over", 850, 400); // Y좌표를 높게 배치

        // 상위 5명 랭킹 보드 표시
        
        renderText("Your Score: " + playerScore, 850, 500);


        // 메인 메뉴로 돌아가기 안내
        renderText("Press ENTER to restart GAME", 850, 650);
    }

    
    
    private void renderTimer() {
        long elapsedTime = System.currentTimeMillis() - gameStartTime;
        long remainingTime = Math.max(0, GAME_DURATION - elapsedTime);

        int minutes = (int) (remainingTime / 60000);
        int seconds = (int) ((remainingTime % 60000) / 1000);

        String timerText = String.format("Time: %02d:%02d", minutes, seconds);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, 1920, 1080, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor3f(1.0f, 1.0f, 1.0f); // 흰색 텍스트

        renderText(timerText, 50, 50); // 좌측 상단에 타이머 표시

        glDisable(GL_BLEND);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }
    

    private void renderScore() {
        String scoreText = "Score: " + score;

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, 1920, 1080, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // 점수 텍스트를 화면 우측 상단에 표시
        glColor3f(1.0f, 1.0f, 1.0f);
        renderText(scoreText, 1700, 50);

        glDisable(GL_BLEND);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }

    private void renderWalls() {
        glEnable(GL_TEXTURE_2D);

        float wallHeight = 20.0f; // 벽 높이
        float wallDistance = 45.0f; // 좌우 벽 거리
        float tileSize = 0.13f; // 타일 크기 증가

        // 전면 벽
        glBindTexture(GL_TEXTURE_2D, frontWallTexture);
        glBegin(GL_QUADS);
        glTexCoord2f(0.0f, 0.0f);
        glVertex3f(-wallDistance, -1.0f, -100.0f); // 좌하단
        glTexCoord2f(0.0f, wallHeight * tileSize);
        glVertex3f(-wallDistance, wallHeight, -100.0f); // 좌상단
        glTexCoord2f(wallDistance * tileSize, wallHeight * tileSize);
        glVertex3f(wallDistance, wallHeight, -100.0f); // 우상단
        glTexCoord2f(wallDistance * tileSize, 0.0f);
        glVertex3f(wallDistance, -1.0f, -100.0f); // 우하단
        glEnd();

        // 좌측 벽
        glBindTexture(GL_TEXTURE_2D, wallTexture);
        glBegin(GL_QUADS);
        glTexCoord2f(0.0f, 0.0f);
        glVertex3f(-wallDistance, -1.0f, -100.0f); // 좌하단
        glTexCoord2f(0.0f, wallHeight * tileSize);
        glVertex3f(-wallDistance, wallHeight, -100.0f); // 좌상단
        glTexCoord2f(50.0f * tileSize, wallHeight * tileSize);
        glVertex3f(-wallDistance, wallHeight, 50.0f); // 우상단
        glTexCoord2f(50.0f * tileSize, 0.0f);
        glVertex3f(-wallDistance, -1.0f, 50.0f); // 우하단
        glEnd();
        
        //우측벽
        glBindTexture(GL_TEXTURE_2D, wallTexture);
        glBegin(GL_QUADS);
        glTexCoord2f(0.0f, 0.0f);
        glVertex3f(wallDistance, -1.0f, -100.0f); // 좌하단
        glTexCoord2f(0.0f, wallHeight * tileSize);
        glVertex3f(wallDistance, wallHeight, -100.0f); // 좌상단
        glTexCoord2f(50.0f * tileSize, wallHeight * tileSize);
        glVertex3f(wallDistance, wallHeight, 50.0f); // 우상단
        glTexCoord2f(50.0f * tileSize, 0.0f);
        glVertex3f(wallDistance, -1.0f, 50.0f); // 우하단
        glEnd();

        glDisable(GL_TEXTURE_2D);
    }


    private void renderFloor() {
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, floorTexture);

        float tileSize = 0.3f; // 기존보다 더 큰 타일 크기 설정
        float floorSize = 150.0f; // 바닥 크기

        glPushMatrix();
        glBegin(GL_QUADS);

        // 텍스처 타일링 적용
        glTexCoord2f(0.0f, 0.0f);
        glVertex3f(-floorSize, -1.0f, -floorSize); // 좌하단
        glTexCoord2f(0.0f, floorSize * tileSize);
        glVertex3f(-floorSize, -1.0f, floorSize); // 좌상단
        glTexCoord2f(floorSize * tileSize, floorSize * tileSize);
        glVertex3f(floorSize, -1.0f, floorSize); // 우상단
        glTexCoord2f(floorSize * tileSize, 0.0f);
        glVertex3f(floorSize, -1.0f, -floorSize); // 우하단

        glEnd();
        glPopMatrix();

        glDisable(GL_TEXTURE_2D);
    }


    private void renderEnemies() {
        long currentTime = System.currentTimeMillis();
        Iterator<Enemy> iterator = enemies.iterator();

        while (iterator.hasNext()) {
            Enemy enemy = iterator.next();

            if (enemy.isAlive) {
                // 생존 시간이 지나면 적 제거
                if (currentTime - enemy.spawnTime >= enemy.lifetime) {
                    iterator.remove();
                    continue;
                }

                // 이동 처리
                enemy.move();

                // 알파 블렌딩 활성화
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

                // 텍스처 활성화 및 바인드
                glEnable(GL_TEXTURE_2D);
                if (enemy.color[0] == 1.0f && enemy.color[1] == 0.0f && enemy.color[2] == 0.0f) {
                    glBindTexture(GL_TEXTURE_2D, redTargetTexture); // 빨강 타겟
                } else if (enemy.color[0] == 0.0f && enemy.color[1] == 0.0f && enemy.color[2] == 1.0f) {
                    glBindTexture(GL_TEXTURE_2D, blueTargetTexture); // 파랑 타겟
                } else if (enemy.color[0] == 1.0f && enemy.color[1] == 1.0f && enemy.color[2] == 0.0f) {
                    glBindTexture(GL_TEXTURE_2D, yellowTargetTexture); // 노랑 타겟
                }

                // 타겟 렌더링
                renderFlatCircle(1.0f, 32, enemy); // 평면 원 렌더링

                // 텍스처 및 블렌딩 비활성화
                glDisable(GL_TEXTURE_2D);
                glDisable(GL_BLEND);
            }
        }
    }

    
    private void renderFlatCircle(float radius, int segments, Enemy enemy) {
        // 플레이어와 타겟 사이의 상대 위치 계산
        float dx = playerX - enemy.x;
        float dz = playerZ - enemy.z;

        // 플레이어가 바라보는 방향의 반대편 계산
        float targetYaw = (float) Math.toDegrees(Math.atan2(dx, dz)) - cameraYaw;

        // 타겟 렌더링
        glPushMatrix();
        glTranslatef(enemy.x, enemy.y, enemy.z); // 타겟 위치로 이동
        glRotatef(targetYaw, 0.0f, 1.0f, 0.0f); // Y축 기준으로 회전
        glRotatef(90, 1.0f, 0.0f, 0.0f); // X축 기준으로 상하 90도 회전

        // OpenGL로 평면 원 렌더링
        glBegin(GL_TRIANGLE_FAN);
        glTexCoord2f(0.5f, 0.5f); // 중심점 텍스처 좌표
        glVertex3f(0.0f, 0.0f, 0.0f); // 중심점
        for (int i = 0; i <= segments; i++) {
            float theta = (float) (2.0f * Math.PI * i / segments);
            float x = (float) (radius * Math.cos(theta));
            float z = (float) (radius * Math.sin(theta));

            // 텍스처 좌표 계산
            float u = 0.5f + 0.5f * (float) Math.cos(theta);
            float v = 0.5f + 0.5f * (float) Math.sin(theta);

            glTexCoord2f(u, v);
            glVertex3f(x, 0.0f, z);
        }
        glEnd();

        glPopMatrix();
    }



    private void renderCrosshair() {
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(-1, 1, -1, 1, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glBegin(GL_LINES);
        glColor3f(1.0f, 1.0f, 1.0f); // 크로스헤어는 흰색

        glVertex2f(-0.02f, 0.0f);
        glVertex2f(0.02f, 0.0f);

        glVertex2f(0.0f, -0.02f);
        glVertex2f(0.0f, 0.02f);

        glEnd();

        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }


    private void cleanup() {
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }
    
    class Enemy {
        float x, y, z;          // 위치
        boolean isAlive;        // 살아있는 상태
        long spawnTime;         // 생성 시간
        float[] color;          // 적의 색상 (RGB 배열)
        long lifetime;          // 적이 유지될 시간 (밀리초 단위)
        float speedX;           // X 방향 속도
        float speedY;           // Y 방향 속도
        boolean reverseX;       // X 방향 반전 여부
        boolean reverseY;       // Y 방향 반전 여부

        private static final float MIN_Y = 1.0f; // 바닥 위 최소 Y 값
        private static final float MAX_Y = 10.0f; // 최대 Y 값

        // 생성자
        Enemy(float x, float y, float z, float[] color, float speedX, float speedY) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color; // RGB 색상
            this.lifetime = 3000; // 모든 표적의 생존 시간은 3초
            this.isAlive = true;
            this.spawnTime = System.currentTimeMillis(); // 생성 시간 기록
            this.speedX = speedX; // X 방향 속도
            this.speedY = speedY; // Y 방향 속도
            this.reverseX = false; // 초기에는 방향 반전 없음
            this.reverseY = false;
        }

        // 표적 이동 메서드
        void move() {
            // 좌우 왕복
            if (Math.abs(x) > 20.0f) { // X축 20 단위 이상 이동하면 반전
                reverseX = !reverseX;
            }
            if (y > MAX_Y || y < MIN_Y) { // Y축이 범위를 벗어나면 반전
                reverseY = !reverseY;
            }

            // 반전 상태에 따라 속도 반전
            this.x += reverseX ? -speedX : speedX;
            this.y += reverseY ? -speedY : speedY;

            // Y 좌표를 범위 내로 유지
            this.y = Math.max(MIN_Y, Math.min(MAX_Y, this.y));
        }
    }


    private void spawnEnemy() {
        if (random.nextFloat() > 0.66f) { // 1/3 빈도로 적 생성
            return; // 적을 생성하지 않음
        }

        float minDistance = 20.0f; // 플레이어와의 최소 전면 거리
        float maxX = 40.0f * 0.85f; // X축 범위를 기존의 85%로 축소
        float maxZ = -80.0f; // Z축 범위 (전면)

        float x, z;
        do {
            // X축 좌우 분산 계산 (좌우 범위 축소)
            x = (random.nextFloat() - 0.5f) * maxX * 2; // -maxX ~ maxX 범위

            // Z축은 항상 플레이어 전면에 생성
            z = playerZ - (minDistance + random.nextFloat() * (Math.abs(maxZ) - minDistance));
        } while (Math.sqrt(x * x + z * z) < minDistance); // 최소 거리 조건 유지

        float y = 1.0f; // 타겟 높이

        // 랜덤 색상 및 움직임 방향 설정
        float[][] colors = {
            {1.0f, 0.0f, 0.0f}, // 빨강
            {1.0f, 1.0f, 0.0f}, // 노랑
            {0.0f, 0.0f, 1.0f}  // 파랑
        };

        int index = random.nextInt(colors.length);
        float[] color = colors[index];

        // 속도 설정 (이동 속도 감소 및 랜덤화)
        float speedX = 0.0f;
        float speedY = 0.0f;

        if (color[0] == 1.0f && color[1] == 0.0f && color[2] == 0.0f) {
            speedX = (random.nextFloat() - 0.5f) * 0.1f; // -0.01 ~ 0.01
            speedY = (random.nextFloat() - 0.5f) * 0.1f; // -0.01 ~ 0.01
        
        } else if (color[0] == 1.0f && color[1] == 1.0f && color[2] == 0.0f) {
            // 노란색 표적: 좌우로 왕복
            speedX = 0.02f; // X축으로 움직임
        } else if (color[0] == 0.0f && color[1] == 0.0f && color[2] == 1.0f) {
            // 파란색 표적: 위쪽 방향으로만 움직임 (Y 방향 증가)
            speedY = 0.01f; // 위쪽으로 천천히 이동
        }

        // 적 생성 및 추가
        Enemy enemy = new Enemy(x, y, z, color, speedX, speedY);
        enemies.add(enemy);
    }
    

    private void checkMouseClick() {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastClickTime >= 100 && glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS) {
            if (isReloading) {
                return; // 재장전 중에는 발사 불가
            }

            if (ammo > 0) {
                soundPlayer.playSound("C:/java_workspace/HelloLWJGLProject/src/sound/laserSound.wav");
                ammo--;
                lastClickTime = currentTime;

                // 반동 추가
                recoilPitch -= 0.5f;
                recoilYaw += (Math.random() - 0.5f) * 0.3f;

                for (Enemy enemy : enemies) {
                    if (enemy.isAlive && isEnemyInCrosshair(enemy)) {
                        enemy.isAlive = false;
                        score += (enemy.color[0] == 1.0f) ? 3 : (enemy.color[1] == 1.0f) ? 2 : 1;
                        soundPlayer.playSound("C:/java_workspace/HelloLWJGLProject/src/sound/targetSound.wav");
                        break;
                    }
                }
            } else {
                System.out.println("Out of ammo! Press R to reload.");
            }
        }
    }
    
    private void applyRecoilRecovery() {
        // 수직 반동 복구
        if (recoilPitch < 0) {
            recoilPitch = Math.min(0, recoilPitch + recoilRecoverySpeed);
        }

        // 수평 반동 복구
        if (recoilYaw > 0) {
            recoilYaw = Math.max(0, recoilYaw - recoilRecoverySpeed);
        } else if (recoilYaw < 0) {
            recoilYaw = Math.min(0, recoilYaw + recoilRecoverySpeed);
        }
    }


    private void checkReloadKey() {
        if (glfwGetKey(window, GLFW_KEY_R) == GLFW_PRESS && !isReloading && ammo < maxAmmo) {
            isReloading = true;

            // 재장전 사운드 재생
            soundPlayer.playSound("C:/java_workspace/HelloLWJGLProject/src/sound/reloadSound.wav");

            // 새로운 스레드로 재장전 처리
            new Thread(() -> {
                try {
                    Thread.sleep(2000); // 2초 동안 재장전
                    ammo = maxAmmo; // 탄창 채우기
                    System.out.println("Reload complete! Ammo refilled to " + maxAmmo);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    isReloading = false;
                }
            }).start();
        }
    }








    private boolean isEnemyInCrosshair(Enemy enemy) {
        float dx = enemy.x - playerX;
        float dy = enemy.y - cameraHeight;
        float dz = enemy.z - playerZ;

        float cosYaw = (float) Math.cos(Math.toRadians(cameraYaw));
        float sinYaw = (float) Math.sin(Math.toRadians(cameraYaw));
        float rotatedX = dx * cosYaw + dz * sinYaw;
        float rotatedZ = -dx * sinYaw + dz * cosYaw;

        float cosPitch = (float) Math.cos(Math.toRadians(cameraPitch));
        float sinPitch = (float) Math.sin(Math.toRadians(cameraPitch));
        float rotatedY = dy * cosPitch - rotatedZ * sinPitch;
        float finalZ = rotatedZ * cosPitch + dy * sinPitch;

        if (finalZ >= -1.0f) {
            return false;
        }

        float screenX = rotatedX / -finalZ;
        float screenY = rotatedY / -finalZ;

        float threshold = 0.02f;
        return Math.abs(screenX) < threshold && Math.abs(screenY) < threshold;
    }

    private void updateCameraRotation() {
        double[] xpos = new double[1];
        double[] ypos = new double[1];
        glfwGetCursorPos(window, xpos, ypos);

        float sensitivity = 0.1f;
        cameraYaw += (float) (xpos[0] - 960) * sensitivity;
        cameraPitch += (float) (ypos[0] - 540) * sensitivity;

        cameraPitch = Math.max(-90, Math.min(90, cameraPitch));
        glfwSetCursorPos(window, 960, 540);
    }

    private void updatePlayerPosition() {
        float cosYaw = (float) Math.cos(Math.toRadians(cameraYaw));
        float sinYaw = (float) Math.sin(Math.toRadians(cameraYaw));

        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            playerX += sinYaw * moveSpeed;
            playerZ -= cosYaw * moveSpeed;
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            playerX -= sinYaw * moveSpeed;
            playerZ += cosYaw * moveSpeed;
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            playerX -= cosYaw * moveSpeed;
            playerZ -= sinYaw * moveSpeed;
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            playerX += cosYaw * moveSpeed;
            playerZ += sinYaw * moveSpeed;
        }
    }

    public static void main(String[] args) {
        new ShootingGame().run();
    }
}
