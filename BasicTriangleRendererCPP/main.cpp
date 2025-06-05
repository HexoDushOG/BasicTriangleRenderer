#include <raylib.h>
#include <rlgl.h>
#include <math.h>

int main() {
    SetConfigFlags(FLAG_WINDOW_RESIZABLE);
    InitWindow(800, 600, "BasicTriangleRenderer");

    // Initialize variables for color shifting
    float time = 0.0f;

    while (!WindowShouldClose()) {
        // Update time for color animation
        time += GetFrameTime();

        BeginDrawing();
        ClearBackground({0, 0, 0, 255});

        rlMatrixMode(RL_PROJECTION);
        rlLoadIdentity();

        // Calculate color values using sine waves with different frequencies
        // This creates a smooth color shifting effect
        float red1 = (sinf(time * 1.7f) + 1.0f) * 0.5f;
        float green1 = (sinf(time * 2.3f + 2.0f) + 1.0f) * 0.5f;
        float blue1 = (sinf(time * 1.5f + 4.0f) + 1.0f) * 0.5f;

        float red2 = (sinf(time * 2.1f + 1.0f) + 1.0f) * 0.5f;
        float green2 = (sinf(time * 1.8f + 3.0f) + 1.0f) * 0.5f;
        float blue2 = (sinf(time * 2.5f + 5.0f) + 1.0f) * 0.5f;

        float red3 = (sinf(time * 1.3f + 3.0f) + 1.0f) * 0.5f;
        float green3 = (sinf(time * 2.0f + 1.5f) + 1.0f) * 0.5f;
        float blue3 = (sinf(time * 1.9f + 0.5f) + 1.0f) * 0.5f;

        rlBegin(RL_TRIANGLES);
        rlColor4f(red1, green1, blue1, 1.0f); rlVertex3f(+0.0f, +0.5f, 0.0f);
        rlColor4f(red2, green2, blue2, 1.0f); rlVertex3f(-0.5f, -0.5f, 0.0f);
        rlColor4f(red3, green3, blue3, 1.0f); rlVertex3f(+0.5f, -0.5f, 0.0f);
        rlEnd();

        EndDrawing();
    }

    CloseWindow();
    return 0;
}

